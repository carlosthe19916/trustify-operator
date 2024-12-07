package org.trustify.operator.controllers;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.ingress.AppIngress;
import org.trustify.operator.controllers.setup.K3sResource;

import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class HostnameSpecTest extends ReconcilerBaseTest {

    @Test
    public void customHostname() throws InterruptedException {
        String host = "app1.example.com";

        // Create
        final Trustify trustify = generateTrustify("custom-hostname");
        trustify.setSpec(new TrustifySpec(
                null,
                null,
                null,
                null,
                null,
                null,
                new TrustifySpec.HostnameSpec(
                        host
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
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    verifyDatabase(trustify);
                    verifyServer(trustify);
                    verifyUI(trustify);
                    verifyIngress(trustify, false);

                    // Ingress
                    final var ingress = client.network().v1().ingresses()
                            .inNamespace(client.getNamespace())
                            .withName(AppIngress.getIngressName(trustify))
                            .get();

                    final var rules = ingress.getSpec().getRules();
                    Assertions.assertEquals(host, rules.getFirst().getHost());
                });
    }

}