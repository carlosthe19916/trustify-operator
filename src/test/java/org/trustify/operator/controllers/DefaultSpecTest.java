package org.trustify.operator.controllers;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.controllers.setup.K3sResource;

import java.util.concurrent.TimeUnit;

@QuarkusTestResource(K3sResource.class)
@QuarkusTest
public class DefaultSpecTest extends ReconcilerBaseTest {

    @Test
    public void defaultSpec() throws InterruptedException {
        final Trustify trustify = generateTrustify("default-spec");
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
                });
    }

}