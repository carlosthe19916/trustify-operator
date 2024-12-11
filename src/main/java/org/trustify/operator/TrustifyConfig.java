package org.trustify.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "trustify")
public interface TrustifyConfig {

    @WithName("default-pvc-size")
    String defaultPvcSize();

    @WithName("default-requested-cpu")
    String defaultRequestedCpu();

    @WithName("default-requested-memory")
    String defaultRequestedMemory();

    @WithName("default-limit-cpu")
    String defaultLimitCpu();

    @WithName("default-limit-memory")
    String defaultLimitMemory();

    @WithName("keycloak-operator.subscription")
    Optional<KeycloakSubscriptionConfig> keycloakSubscriptionConfig();

    @WithName("keycloak-operator.resources")
    Optional<KeycloakResources> keycloakResources();

    interface KeycloakSubscriptionConfig {
        @WithName("namespace")
        String namespace();

        @WithName("source")
        String source();

        @WithName("channel")
        String channel();
    }

    interface KeycloakResources {
        @WithName("requests.memory")
        Optional<String> requestedMemory();

        @WithName("requests.cpu")
        Optional<String> requestedCPU();

        @WithName("limits.memory")
        Optional<String> limitMemory();

        @WithName("limits.cpu")
        Optional<String> limitCPU();
    }
}
