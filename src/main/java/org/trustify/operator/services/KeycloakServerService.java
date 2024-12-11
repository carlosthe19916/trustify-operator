package org.trustify.operator.services;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.keycloak.k8s.v2alpha1.Keycloak;
import org.keycloak.k8s.v2alpha1.KeycloakSpec;
import org.keycloak.k8s.v2alpha1.keycloakspec.*;
import org.keycloak.k8s.v2alpha1.keycloakspec.db.PasswordSecret;
import org.keycloak.k8s.v2alpha1.keycloakspec.db.UsernameSecret;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.deployment.KeycloakDBDeployment;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.secret.KeycloakDBSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.service.KeycloakDBService;
import org.trustify.operator.utils.CRDUtils;

import java.util.*;

@ApplicationScoped
public class KeycloakServerService {

    public static final String RELATIVE_PATH = "/auth";

    @Inject
    KubernetesClient k8sClient;

    @Inject
    TrustifyConfig trustifyConfig;

    @Inject
    ClusterService clusterService;

    public static String getKeycloakName(Trustify cr) {
        return cr.getMetadata().getName() + "-keycloak";
    }

    public Keycloak initInstance(Trustify cr) {
        Keycloak keycloak = new Keycloak();

        keycloak.setMetadata(new ObjectMeta());
        keycloak.getMetadata().setName(getKeycloakName(cr));
        keycloak.setSpec(new KeycloakSpec());

        KeycloakSpec spec = keycloak.getSpec();
        spec.setInstances(1L);

        // Resources
        trustifyConfig.keycloakResources()
                .ifPresent(resources -> {
                    spec.setResources(new Resources());

                    HashMap<String, IntOrString> requests = new HashMap<>();
                    spec.getResources().setRequests(requests);
                    resources.requestedMemory().ifPresent(value -> requests.put("memory", new IntOrString(value)));
                    resources.requestedCPU().ifPresent(value -> requests.put("cpu", new IntOrString(value)));

                    HashMap<String, IntOrString> limits = new HashMap<>();
                    spec.getResources().setLimits(limits);
                    resources.limitMemory().ifPresent(value -> limits.put("memory", new IntOrString(value)));
                    resources.limitCPU().ifPresent(value -> limits.put("cpu", new IntOrString(value)));
                });

        // Database
        Db dbSpec = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> Optional.ofNullable(oidcSpec.embeddedOidcSpec()))
                .flatMap(embeddedOidcSpec -> Optional.ofNullable(embeddedOidcSpec.databaseSpec()))
                .flatMap(databaseSpec -> {
                    Db db = null;
                    if (databaseSpec.externalDatabase()) {
                        db = new Db();

                        db.setVendor("postgres");
                        db.setHost(databaseSpec.host());
                        db.setPort(Long.parseLong(databaseSpec.port()));
                        db.setDatabase(databaseSpec.name());

                        UsernameSecret usernameSecret = new UsernameSecret();
                        usernameSecret.setName(databaseSpec.usernameSecret().getName());
                        usernameSecret.setKey(databaseSpec.usernameSecret().getKey());
                        db.setUsernameSecret(usernameSecret);

                        PasswordSecret passwordSecret = new PasswordSecret();
                        passwordSecret.setName(databaseSpec.passwordSecret().getName());
                        passwordSecret.setKey(databaseSpec.passwordSecret().getKey());
                        db.setPasswordSecret(passwordSecret);
                    }
                    return Optional.ofNullable(db);
                })
                .orElseGet(() -> {
                    Db db = new Db();

                    db.setVendor("postgres");
                    db.setHost(KeycloakDBService.getServiceName(cr));
                    db.setPort(5432L);
                    db.setDatabase(KeycloakDBDeployment.getDatabaseName(cr));

                    SecretKeySelector usernameKeySelector = KeycloakDBSecret.getUsernameKeySelector(cr);
                    UsernameSecret usernameSecret = new UsernameSecret();
                    usernameSecret.setName(usernameKeySelector.getName());
                    usernameSecret.setKey(usernameKeySelector.getKey());
                    db.setUsernameSecret(usernameSecret);

                    SecretKeySelector passwordKeySelector = KeycloakDBSecret.getPasswordKeySelector(cr);
                    PasswordSecret passwordSecret = new PasswordSecret();
                    passwordSecret.setName(passwordKeySelector.getName());
                    passwordSecret.setKey(passwordKeySelector.getKey());
                    db.setPasswordSecret(passwordSecret);

                    return db;
                });

        spec.setDb(dbSpec);

        // Ingress
        spec.setIngress(new Ingress());
        spec.getIngress().setEnabled(false);

        // Https
        spec.setHttp(new Http());
        Http httpSpec = spec.getHttp();

        Optional<Secret> tlsSecret = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> Objects.equals(oidcSpec.type(), TrustifySpec.OidcProviderType.EXTERNAL) ?
                        Optional.ofNullable(oidcSpec.externalOidcSpec()).map(TrustifySpec.ExternalOidcSpec::tlsSecret) :
                        Optional.ofNullable(oidcSpec.embeddedOidcSpec()).map(TrustifySpec.EmbeddedOidcSpec::tlsSecret)
                )
                .flatMap(secretName -> {
                    Secret secret = new SecretBuilder()
                            .withNewMetadata()
                            .withName(secretName)
                            .endMetadata()
                            .build();
                    secret = k8sClient.resource(secret)
                            .inNamespace(cr.getMetadata().getNamespace())
                            .get();
                    return Optional.ofNullable(secret);
                })
                .or(() -> clusterService.getCluster().getAutoGeneratedKeycloakTlsSecret(cr));

        tlsSecret.ifPresentOrElse(secret -> {
            httpSpec.setHttpEnabled(false);
            httpSpec.setTlsSecret(secret.getMetadata().getName());
        }, () -> {
            httpSpec.setHttpEnabled(true);
        });

        // Hostname
        String protocol = spec.getHttp().getHttpEnabled() ? "http" : "https";
        String hostname = CRDUtils.getValueFromSubSpec(cr.getSpec().hostnameSpec(), TrustifySpec.HostnameSpec::hostname)
                .or(() -> clusterService.getCluster().getAutoGeneratedIngressHost(cr)
                        .or(() -> clusterService.getCluster().getClusterHost(cr))
                )
                .orElseThrow(() -> new IllegalStateException("Could not find hostname for setting up Keycloak"));

        spec.setHostname(new Hostname());
        spec.getHostname().setHostname(protocol + "://" + hostname + RELATIVE_PATH);
        spec.getHostname().setBackchannelDynamic(true);

        // Additional options
        AdditionalOptions proxyHeaders = new AdditionalOptions();
        proxyHeaders.setName("proxy-headers");
        proxyHeaders.setValue("xforwarded");

        AdditionalOptions httpRelativePath = new AdditionalOptions();
        httpRelativePath.setName("http-relative-path");
        httpRelativePath.setValue(RELATIVE_PATH);

        AdditionalOptions httpManagementRelativePath = new AdditionalOptions();
        httpManagementRelativePath.setName("http-management-relative-path");
        httpManagementRelativePath.setValue(RELATIVE_PATH);

        spec.setAdditionalOptions(List.of(
                proxyHeaders,
                httpRelativePath,
                httpManagementRelativePath
        ));

        return k8sClient.resource(keycloak)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    public Optional<Keycloak> getCurrentInstance(Trustify cr) {
        Keycloak keycloak = k8sClient.resources(Keycloak.class)
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(getKeycloakName(cr))
                .get();
        return Optional.ofNullable(keycloak);
    }

    public void cleanupDependentResources(Trustify cr) {
        getCurrentInstance(cr).ifPresent(keycloak -> {
            k8sClient.resource(keycloak).delete();
        });
    }

}
