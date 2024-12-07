package org.trustify.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

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
}
