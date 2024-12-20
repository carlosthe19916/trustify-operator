package org.trustify.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "related.image")
public interface TrustifyImagesConfig {

    @WithName("ui")
    String uiImage();

    @WithName("server")
    String serverImage();

    @WithName("db")
    String dbImage();

    @WithName("keycloak")
    String keycloak();

    @WithName("pull-policy")
    String imagePullPolicy();
}
