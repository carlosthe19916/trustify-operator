package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.UIDeployment;
import org.trustify.operator.controllers.setup.K3sWithOlmResource;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sWithOlmResource.class)
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
                new TrustifySpec.OidcSpec(
                        false,
                        null,
                        null,
                        null
                ),
                null,
                null,
                null
        ));

        createTrustify(trustify);

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify);

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
                    Optional<EnvVar> uiAuthEnv = uiDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_REQUIRED", envVar.getName()))
                            .findFirst();

                    Assertions.assertTrue(uiAuthEnv.isPresent());
                    Assertions.assertEquals("false", uiAuthEnv.get().getValue());
                });
    }

    @Test
    public void externalOidc() throws InterruptedException {
        // Create oidc
        InputStream postgresqlYaml = DatabaseSpecTest.class.getClassLoader().getResourceAsStream("helpers/example-keycloak.yaml");
        var resources = client.load(postgresqlYaml).inNamespace(getNamespaceName());
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
                            .withName("keycloak")
                            .get();
                    Assertions.assertNotNull(job);
                    Assertions.assertEquals(1, job.getStatus().getSucceeded(), "Keycloak configuration was not applied");

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
                new TrustifySpec.OidcSpec(
                        true,
                        TrustifySpec.OidcProviderType.EXTERNAL,
                        null,
                        new TrustifySpec.ExternalOidcSpec(
                                "http://keycloak." + getNamespaceName() + ".svc:8080/realms/trustify",
                                "frontend",
                                "backend",
                                null
                        )
                ),
                null,
                null,
                null
        ));

        createTrustify(trustify);

        // Verify resources
        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify);

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
                    Assertions.assertEquals("false", serverAuthEnv.get().getValue());

                    // UI
                    final var uiDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(UIDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<EnvVar> uiAuthEnv = uiDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_REQUIRED", envVar.getName()))
                            .findFirst();

                    Assertions.assertTrue(uiAuthEnv.isPresent());
                    Assertions.assertEquals("true", uiAuthEnv.get().getValue());
                });
    }

    @Test
    public void embeddedOidc() throws InterruptedException {
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
                new TrustifySpec.OidcSpec(
                        true,
                        TrustifySpec.OidcProviderType.EMBEDDED,
                        null,
                        null
                ),
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
                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify);

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
                    Assertions.assertEquals("false", serverAuthEnv.get().getValue());

                    // UI
                    final var uiDeployment = client.apps()
                            .deployments()
                            .inNamespace(trustify.getMetadata().getNamespace())
                            .withName(UIDeployment.getDeploymentName(trustify))
                            .get();
                    Optional<EnvVar> uiAuthEnv = uiDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .getFirst()
                            .getEnv()
                            .stream().filter(envVar -> Objects.equals("AUTH_REQUIRED", envVar.getName()))
                            .findFirst();

                    Assertions.assertTrue(uiAuthEnv.isPresent());
                    Assertions.assertEquals("true", uiAuthEnv.get().getValue());
                });
    }

}