package org.trustify.operator.services;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
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

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

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
                .flatMap(oidcSpec -> Optional.ofNullable(oidcSpec.databaseSpec()))
                .flatMap(embeddedDbSpec -> {
                    Db db = null;
                    if (embeddedDbSpec.externalDatabase()) {
                        TrustifySpec.ExternalOidcDatabaseSpec databaseSpec = embeddedDbSpec.externalDatabaseSpec();

                        db = new Db();

                        db.setVendor(databaseSpec.vendor());
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
                    db.setHost(KeycloakDBService.getServiceHost(cr));
                    db.setPort((long) KeycloakDBService.getServicePort(cr));
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

        Optional<String> tlsSecret = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> oidcSpec.externalServer() ?
                        Optional.ofNullable(oidcSpec.externalOidcSpec()).map(TrustifySpec.ExternalOidcSpec::tlsSecret) :
                        Optional.ofNullable(oidcSpec.embeddedOidcSpec()).map(TrustifySpec.EmbeddedOidcSpec::tlsSecret)
                );

        tlsSecret.ifPresentOrElse(secret -> {
            httpSpec.setHttpEnabled(false);
            httpSpec.setTlsSecret(secret);
        }, () -> {
            httpSpec.setHttpEnabled(true);
        });

        // Hostname
        String protocol = "https"; // We expose the ingress/route always through HTTPS therefore we use https here.
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

    public static String getServiceHost(Trustify cr) {
        return String.format("%s.%s.svc", cr.getMetadata().getName() + "-keycloak-service", cr.getMetadata().getNamespace());
    }

    public static String getServiceUrl(Trustify cr, Keycloak keycloak) {
        String protocol = keycloak.getSpec().getHttp().getHttpEnabled() ? "http" : "https";
        int port = keycloak.getSpec().getHttp().getHttpEnabled() ? 8080 : 8443;
        return String.format("%s://%s:%s", protocol, KeycloakServerService.getServiceHost(cr), port);
    }

    public void cleanupDependentResources(Trustify cr) {
        getCurrentInstance(cr).ifPresent(keycloak -> {
            k8sClient.resource(keycloak).delete();
        });
    }

}
