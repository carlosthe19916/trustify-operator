package org.trustify.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "keycloak-operator.subscription")
public interface KeycloakSubscriptionConfig {

    @WithName("namespace")
    Optional<String> namespace();

    @WithName("source")
    Optional<String> source();

    @WithName("channel")
    Optional<String> channel();
}
