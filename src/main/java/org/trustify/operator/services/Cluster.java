package org.trustify.operator.services;

import java.util.Optional;

public interface Cluster {
    Optional<String> getClusterHostname();
}
