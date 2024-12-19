package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;
import org.trustify.operator.controllers.setup.K3sResource;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class DatabaseSpecTest extends ReconcilerBaseTest {

    @Test
    public void externalDatabase() throws InterruptedException {
        // Create external database
        InputStream postgresqlYaml = DatabaseSpecTest.class.getClassLoader().getResourceAsStream("helpers/example-postgres.yaml");
        var resources = client.load(postgresqlYaml).inNamespace(getNamespaceName());
        createResources(resources);

        Awaitility.await()
                .ignoreException(NullPointerException.class)
                .atMost(3, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    final var statefulSet = client.apps()
                            .statefulSets()
                            .inNamespace(getNamespaceName())
                            .withName("postgresql-db")
                            .get();
                    Assertions.assertEquals(1, statefulSet.getStatus().getReadyReplicas(), "External DB number of replicas is wrong");

                    final var dbService = client.services()
                            .inNamespace(getNamespaceName())
                            .withName("postgresql-db")
                            .get();
                    final var dbPort = dbService.getSpec()
                            .getPorts()
                            .getFirst()
                            .getPort();
                    MatcherAssert.assertThat("DB service port not valid", dbPort, Matchers.is(5432));
                });

        // Create
        final Trustify trustify = generateTrustify("external-database");
        trustify.setSpec(new TrustifySpec(
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustifySpec.DatabaseSpec(
                        true,
                        new TrustifySpec.ExternalDatabaseSpec(
                                new SecretKeySelector("username", "postgresql-db", false),
                                new SecretKeySelector("password", "postgresql-db", false),
                                "postgresql-db." + getNamespaceName() + ".svc",
                                "5432",
                                "database",
                                null,
                                null,
                                null
                        ),
                        null
                ),
                null,
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
                    // Database should not be created
                    final var dbDeployment = client.apps()
                            .deployments()
                            .inNamespace(getNamespaceName())
                            .withName(DBDeployment.getDeploymentName(trustify))
                            .get();
                    Assertions.assertNull(dbDeployment, "DB should not be created as an external one is used");

                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify, true, false);
                });
    }

    @Test
    public void providedDatabase() throws InterruptedException {
        // Create
        final Trustify trustify = generateTrustify("provided-database");
        trustify.setSpec(new TrustifySpec(
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustifySpec.DatabaseSpec(
                        false,
                        null,
                        null
                ),
                null,
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
                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify, true, false);
                });
    }

}