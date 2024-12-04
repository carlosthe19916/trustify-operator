package org.trustify.operator.cdrs.v2alpha1.keycloak.services;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.ValueOrSecret;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBDeployment;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakDBService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakHttpTlsSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.KeycloakSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.DatabaseSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.HostnameSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.HttpSpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.spec.IngressSpec;
import org.trustify.operator.HostUtils;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class KeycloakServerService {

    public static final String RELATIVE_PATH = "/auth";

    @Inject
    KubernetesClient k8sClient;

    public static String getKeycloakName(Trustify cr) {
        return cr.getMetadata().getName() + "-keycloak";
    }

    public Keycloak initInstance(Trustify cr) {
        Keycloak keycloak = new Keycloak();

        keycloak.setMetadata(new ObjectMeta());
        keycloak.getMetadata().setName(getKeycloakName(cr));
        keycloak.setSpec(new KeycloakSpec());

        KeycloakSpec spec = keycloak.getSpec();
        spec.setInstances(1);

        // Database
        spec.setDatabaseSpec(new DatabaseSpec());
        DatabaseSpec databaseSpec = spec.getDatabaseSpec();
        databaseSpec.setVendor("postgres");
        databaseSpec.setHost(KeycloakDBService.getServiceName(cr));
        databaseSpec.setPort(5432);
        databaseSpec.setDatabase(KeycloakDBDeployment.getDatabaseName(cr));

        databaseSpec.setUsernameSecret(KeycloakDBSecret.getUsernameKeySelector(cr));
        databaseSpec.setPasswordSecret(KeycloakDBSecret.getPasswordKeySelector(cr));

        // Https
        spec.setHttpSpec(new HttpSpec());
        HttpSpec httpSpec = spec.getHttpSpec();
        httpSpec.setTlsSecret(KeycloakHttpTlsSecret.getSecretName(cr));

        // Ingress
        spec.setIngressSpec(new IngressSpec());
        spec.getIngressSpec().setIngressEnabled(false);

        // Hostname
        String hostname = HostUtils.getHostnameForKeycloak(cr, k8sClient)
                .orElseThrow(() -> new IllegalStateException("Could not find hostname for setting up Keycloak"));

        spec.setHostnameSpec(new HostnameSpec());
        spec.getHostnameSpec().setHostname("https://" + hostname + RELATIVE_PATH);
        spec.getHostnameSpec().setBackchannelDynamic(true);

        // Additional options
        spec.setAdditionalOptions(List.of(
                new ValueOrSecret("proxy-headers", "xforwarded", null),
                new ValueOrSecret("http-relative-path", RELATIVE_PATH, null),
                new ValueOrSecret("http-management-relative-path", RELATIVE_PATH, null)
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

    public static String getServiceHost(Trustify cr) {
        return String.format("%s.%s.svc", cr.getMetadata().getName() + "-keycloak-service", cr.getMetadata().getNamespace());
    }

    public static String getServiceHostWithPort(Trustify cr) {
        return String.format("%s:%s", getServiceHost(cr), 8443);
    }

    public static String getServiceHostUrl(Trustify cr) {
        return String.format("%s://%s", "https", getServiceHostWithPort(cr));
    }
}
