package org.trustify.operator.cdrs.v2alpha1.server.deployment;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.TrustifyImagesConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifyConfiguration;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.configmap.ServerConfigMap;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;
import org.trustify.operator.controllers.ResourceConfigurator;
import org.trustify.operator.services.KeycloakRealmService;
import org.trustify.operator.utils.CRDUtils;
import org.trustify.operator.utils.OptionMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ServerDeploymentConfigurator extends TrustifyConfiguration implements ResourceConfigurator {

    @Inject
    TrustifyConfig trustifyConfig;

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    @Inject
    ServerUtils serverUtils;

    @Override
    public Config configureDeployment(Trustify cr, Context<Trustify> context) {
        String image = Optional.ofNullable(cr.getSpec().serverImage()).orElse(trustifyImagesConfig.serverImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());

        List<LocalObjectReference> imagePullSecrets = Optional.ofNullable(cr.getSpec().imagePullSecrets()).orElse(new ArrayList<>());

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), TrustifySpec::serverResourceLimitSpec)
                .orElse(null);
        ResourceRequirements resourceRequirements = CRDUtils.getResourceRequirements(resourcesLimitSpec, trustifyConfig);

        Config config = new Config(
                image,
                imagePullPolicy,
                imagePullSecrets,
                resourceRequirements,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );

        configureGeneral(config, cr);
        configureHttp(config, cr);
        configureDatabase(config, cr);
        configureStorage(config, cr);
        configureOidc(config, cr);

        return config;
    }

    private void configureGeneral(Config config, Trustify cr) {
        config.allEnvVars().add(new EnvVarBuilder()
                .withName("RUST_LOG")
                .withValue("info")
                .build()
        );
        config.allEnvVars().add(new EnvVarBuilder()
                .withName("INFRASTRUCTURE_ENABLED")
                .withValue("true")
                .build()
        );
        config.allEnvVars().add(new EnvVarBuilder()
                .withName("INFRASTRUCTURE_BIND")
                .withValue("[::]:" + ServerDeployment.getDeploymentInfrastructurePort(cr))
                .build()
        );
        config.allEnvVars().add(new EnvVarBuilder()
                .withName("CLIENT_TLS_CA_CERTIFICATES")
                .withValue("/run/secrets/kubernetes.io/serviceaccount/service-ca.crt")
                .build()
        );
    }

    private void configureHttp(Config config, Trustify cr) {
        configureTLS(config, cr);

        config.allEnvVars().add(new EnvVarBuilder()
                .withName("HTTP_SERVER_BIND_ADDR")
                .withValue("::")
                .build()
        );
    }

    private void configureTLS(Config config, Trustify cr) {
        final String certFileOptionName = "HTTP_SERVER_TLS_CERTIFICATE_FILE";
        final String keyFileOptionName = "HTTP_SERVER_TLS_KEY_FILE";

        Optional<String> tlsSecretName = serverUtils.tlsSecretName(cr);
        if (tlsSecretName.isEmpty()) {
            return;
        }

        String certificatesDir = "/opt/trustify/tls-server";

        config.allEnvVars().add(new EnvVarBuilder()
                .withName("HTTP_SERVER_TLS_ENABLED")
                .withValue("true")
                .build()
        );
        config.allEnvVars().add(new EnvVarBuilder()
                .withName(certFileOptionName)
                .withValue(certificatesDir + "/tls.crt")
                .build()
        );
        config.allEnvVars().add(new EnvVarBuilder()
                .withName(keyFileOptionName)
                .withValue(certificatesDir + "/tls.key")
                .build()
        );

        var volume = new VolumeBuilder()
                .withName("tls-server")
                .withNewSecret()
                .withSecretName(tlsSecretName.get())
                .withOptional(false)
                .endSecret()
                .build();

        var volumeMount = new VolumeMountBuilder()
                .withName(volume.getName())
                .withMountPath(certificatesDir)
                .withReadOnly(true)
                .build();

        config.allVolumes().add(volume);
        config.allVolumeMounts().add(volumeMount);
    }

    private void configureOidc(Config config, Trustify cr) {
        Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> {
                    if (!oidcSpec.enabled()) {
                        return Optional.empty();
                    }

                    List<EnvVar> embeddedUIEnvVars;
                    Optional<String> oidcSecretName;
                    if (oidcSpec.externalServer()) {
                        embeddedUIEnvVars = new OptionMapper<>(oidcSpec.externalOidcSpec())
                                .mapOption("UI_ISSUER_URL", TrustifySpec.ExternalOidcSpec::serverUrl)
                                .mapOption("UI_CLIENT_ID", TrustifySpec.ExternalOidcSpec::uiClientId)
                                .getEnvVars();

                        oidcSecretName = Optional.ofNullable(oidcSpec.externalOidcSpec()).map(TrustifySpec.ExternalOidcSpec::tlsSecret);
                    } else {
                        embeddedUIEnvVars = new OptionMapper<>(oidcSpec.embeddedOidcSpec())
//                                .mapOption("UI_ISSUER_URL", embeddedOidcSpec -> AppIngress.getHostname(cr))
                                .mapOption("UI_CLIENT_ID", embeddedOidcSpec -> KeycloakRealmService.getUIClientName(cr))
                                .getEnvVars();

                        oidcSecretName = Optional.ofNullable(oidcSpec.embeddedOidcSpec()).map(TrustifySpec.EmbeddedOidcSpec::tlsSecret);
                    }
                    config.allEnvVars().addAll(embeddedUIEnvVars);


                    oidcSecretName.ifPresent(secretName -> {
                        var oidcTlsVolume = new VolumeBuilder()
                                .withName("tls-oidc")
                                .withSecret(new SecretVolumeSourceBuilder()
                                        .withSecretName(secretName)
                                        .withOptional(true)
                                        .withDefaultMode(420)
                                        .build()
                                )
                                .build();
                        var oidcTlsVolumeVolumeMount = new VolumeMountBuilder()
                                .withName(oidcTlsVolume.getName())
                                .withMountPath(ServerConfigMap.getAuthTlsCaCertificateDirectory(cr))
                                .withReadOnly(true)
                                .build();
                        config.allVolumes().add(oidcTlsVolume);
                        config.allVolumeMounts().add(oidcTlsVolumeVolumeMount);
                    });

                    var authYaml = "/etc/config/auth.yaml";
                    var authVolume = new VolumeBuilder()
                            .withName("auth")
                            .withConfigMap(new ConfigMapVolumeSourceBuilder()
                                    .withName(ServerConfigMap.getConfigMapName(cr))
                                    .withDefaultMode(420)
                                    .build()
                            )
                            .build();
                    var authVolumeMount = new VolumeMountBuilder()
                            .withName(authVolume.getName())
                            .withMountPath(authYaml)
                            .withSubPath(ServerConfigMap.getAuthKey(cr))
                            .build();
                    config.allVolumes().add(authVolume);
                    config.allVolumeMounts().add(authVolumeMount);

                    config.allEnvVars().add(new EnvVarBuilder()
                            .withName("AUTH_CONFIGURATION")
                            .withValue(authYaml)
                            .build()
                    );

                    config.allEnvVars().add(new EnvVarBuilder()
                            .withName("AUTH_DISABLED")
                            .withValue(Boolean.FALSE.toString())
                            .build()
                    );

                    return Optional.of(true);
                })
                .orElseGet(() -> {
                    config.allEnvVars().add(new EnvVarBuilder()
                            .withName("AUTH_DISABLED")
                            .withValue(Boolean.TRUE.toString())
                            .build()
                    );
                    return true;
                });
    }

}
