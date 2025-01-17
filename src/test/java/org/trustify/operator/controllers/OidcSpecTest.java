package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.deployment.UIDeployment;
import org.trustify.operator.controllers.setup.K3sResource;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class OidcSpecTest extends ReconcilerBaseTest {

    @Test
    public void disabledOidc() throws InterruptedException {
        // Create
        final Trustify trustify = generateTrustify("disabled-oidc");
        trustify.setSpec(new TrustifySpec(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustifySpec.OidcSpec(
                        false,
                        false,
                        null,
                        null
                ),
                null,
                null,
                null,
                null
        ));

        createTrustify(trustify);

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(3, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyTrustify(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify, true, false);

                    // Server
                    final var serverDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(ServerDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<EnvVar> serverAuthEnv = serverDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_DISABLED", envVar.getName()))
                            .findFirst();

                    Assertions.assertTrue(serverAuthEnv.isPresent());
                    Assertions.assertEquals("true", serverAuthEnv.get().getValue());

                    // UI
                    final var uiDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(UIDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<String> uiAuthEnv = uiDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_REQUIRED", envVar.getName()))
                            .map(EnvVar::getValue)
                            .findFirst();

                    Assertions.assertEquals("false", uiAuthEnv.orElse(null));
                });
    }

    @Test
    public void externalServer() throws InterruptedException {
        // Create external database
        InputStream keycloakYaml = OidcSpecTest.class.getClassLoader().getResourceAsStream("helpers/example-keycloak.yaml");
        var resources = client.load(keycloakYaml).inNamespace(getNamespaceName());
        createResources(resources);

        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(6, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    final var deployment = client.apps()
                            .deployments()
                            .inNamespace(getNamespaceName())
                            .withName("keycloak")
                            .get();
                    Assertions.assertEquals(1, deployment.getStatus().getReadyReplicas(), "Keycloak not ready");

                    final var job = client.batch()
                            .v1()
                            .jobs()
                            .inNamespace(getNamespaceName())
                            .withName("keycloak-config")
                            .get();
                    Assertions.assertNotNull(job);
                });

        // Create
        final Trustify trustify = generateTrustify("external-oidc");
        trustify.setSpec(new TrustifySpec(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustifySpec.OidcSpec(
                        true,
                        true,
                        new TrustifySpec.ExternalOidcSpec(
                                "http://keycloak." + getNamespaceName() + ".svc:8080/realms/trustify",
                                "frontend",
                                null
                        ),
                        null
                ),
                null,
                null,
                null,
                null
        ));

        createTrustify(trustify);

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(3, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyTrustify(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify, false, true); // Can not test browser as external oidc does not have route/ingress

                    // Server
                    final var serverDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(ServerDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<String> serverAuthEnv = serverDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_DISABLED", envVar.getName()))
                            .map(EnvVar::getValue)
                            .findFirst();

                    Assertions.assertEquals("false", serverAuthEnv.orElse(null));

                    // UI
                    final var uiDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(UIDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<String> authRequired = uiDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_REQUIRED", envVar.getName()))
                            .map(EnvVar::getValue)
                            .findFirst();

                    Assertions.assertEquals("true", authRequired.orElse(null));
                });
    }

    @Tag(TestTags.heavy)
    @Test
    public void embeddedServer() throws InterruptedException {
        // Create
        final Trustify trustify = generateTrustify("embedded-oidc");
        trustify.setSpec(new TrustifySpec(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustifySpec.OidcSpec(
                        true,
                        false,
                        null,
                        null
                ),
                null,
                null,
                null,
                null
        ));

        createTrustify(trustify);

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(5, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyTrustify(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify, true, true);

                    // Server
                    final var serverDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(ServerDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<String> serverAuthEnv = serverDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_DISABLED", envVar.getName()))
                            .map(EnvVar::getValue)
                            .findFirst();

                    Assertions.assertEquals("false", serverAuthEnv.orElse(null));

                    // UI
                    final var uiDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(UIDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<String> uiAuthEnv = uiDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_REQUIRED", envVar.getName()))
                            .map(EnvVar::getValue)
                            .findFirst();

                    Assertions.assertTrue(uiAuthEnv.isPresent());
                    Assertions.assertEquals("true", uiAuthEnv.orElse(null));
                });
    }

}