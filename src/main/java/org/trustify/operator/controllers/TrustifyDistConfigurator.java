package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.configmap.ServerConfigMap;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.db.secret.DBSecret;
import org.trustify.operator.cdrs.v2alpha1.server.db.service.DBService;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.pvc.ServerStoragePersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;
import org.trustify.operator.services.KeycloakRealmService;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class TrustifyDistConfigurator {

    @Inject
    ServerUtils serverUtils;

    public record Config(
            List<EnvVar> allEnvVars,
            List<Volume> allVolumes,
            List<VolumeMount> allVolumeMounts
    ) {
    }

    public Config configureDistOption(Trustify cr) {
        Config config = new Config(
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
        config.allEnvVars.add(new EnvVarBuilder()
                .withName("RUST_LOG")
                .withValue("info")
                .build()
        );
        config.allEnvVars.add(new EnvVarBuilder()
                .withName("INFRASTRUCTURE_ENABLED")
                .withValue("true")
                .build()
        );
        config.allEnvVars.add(new EnvVarBuilder()
                .withName("INFRASTRUCTURE_BIND")
                .withValue("[::]:" + ServerDeployment.getDeploymentInfrastructurePort(cr))
                .build()
        );
        config.allEnvVars.add(new EnvVarBuilder()
                .withName("CLIENT_TLS_CA_CERTIFICATES")
                .withValue("/run/secrets/kubernetes.io/serviceaccount/service-ca.crt")
                .build()
        );
    }

    private void configureHttp(Config config, Trustify cr) {
        configureTLS(config, cr);

        config.allEnvVars.add(new EnvVarBuilder()
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

        config.allEnvVars.add(new EnvVarBuilder()
                .withName("HTTP_SERVER_TLS_ENABLED")
                .withValue("true")
                .build()
        );
        config.allEnvVars.add(new EnvVarBuilder()
                .withName(certFileOptionName)
                .withValue(certificatesDir + "/tls.crt")
                .build()
        );
        config.allEnvVars.add(new EnvVarBuilder()
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

        config.allVolumes.add(volume);
        config.allVolumeMounts.add(volumeMount);
    }

    private void configureDatabase(Config config, Trustify cr) {
        List<EnvVar> envVars = Optional.ofNullable(cr.getSpec().databaseSpec())
                .flatMap(databaseSpec -> {
                    if (databaseSpec.externalDatabase()) {
                        List<EnvVar> envs = optionMapper(databaseSpec.externalDatabaseSpec())
                                .mapOption("TRUSTD_DB_USER", TrustifySpec.ExternalDatabaseSpec::usernameSecret)
                                .mapOption("TRUSTD_DB_PASSWORD", TrustifySpec.ExternalDatabaseSpec::passwordSecret)
                                .mapOption("TRUSTD_DB_NAME", TrustifySpec.ExternalDatabaseSpec::name)
                                .mapOption("TRUSTD_DB_HOST", TrustifySpec.ExternalDatabaseSpec::host)
                                .mapOption("TRUSTD_DB_PORT", TrustifySpec.ExternalDatabaseSpec::port)
                                .getEnvVars();
                        return Optional.of(envs);
                    } else {
                        return Optional.empty();
                    }
                })
                .orElseGet(() -> optionMapper(cr.getSpec())
                        .mapOption("TRUSTD_DB_USER", spec -> DBSecret.getUsernameSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_PASSWORD", spec -> DBSecret.getPasswordSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_NAME", spec -> DBDeployment.getDatabaseName(cr))
                        .mapOption("TRUSTD_DB_HOST", spec -> DBService.getServiceHost(cr))
                        .mapOption("TRUSTD_DB_PORT", spec -> DBDeployment.getDatabasePort(cr))
                        .getEnvVars()
                );

        config.allEnvVars.addAll(envVars);
    }

    private void configureStorage(Config config, Trustify cr) {
        List<EnvVar> envVars = new ArrayList<>();

        TrustifySpec.StorageSpec storageSpec = Optional.ofNullable(cr.getSpec().storageSpec())
                .orElse(new TrustifySpec.StorageSpec(null, null, null, null));

        // Storage type
        TrustifySpec.StorageStrategyType storageStrategyType = Objects.nonNull(storageSpec.type()) ? storageSpec.type() : TrustifySpec.StorageStrategyType.FILESYSTEM;
        envVars.add(new EnvVarBuilder()
                .withName("TRUSTD_STORAGE_STRATEGY")
                .withValue(storageStrategyType.getValue())
                .build()
        );

        // Other config
        envVars.addAll(optionMapper(storageSpec)
                .mapOption("TRUSTD_STORAGE_COMPRESSION", spec -> Objects.nonNull(spec.compression()) ? spec.compression().getValue() : null)
                .getEnvVars()
        );

        switch (storageStrategyType) {
            case FILESYSTEM -> {
                envVars.add(new EnvVarBuilder()
                        .withName("TRUSTD_STORAGE_FS_PATH")
                        .withValue("/opt/trustify/storage")
                        .build()
                );

                var volume = new VolumeBuilder()
                        .withName("trustify-pvol")
                        .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                                .withClaimName(ServerStoragePersistentVolumeClaim.getPersistentVolumeClaimName(cr))
                                .build()
                        )
                        .build();

                var volumeMount = new VolumeMountBuilder()
                        .withName(volume.getName())
                        .withMountPath("/opt/trustify")
                        .build();

                config.allVolumes.add(volume);
                config.allVolumeMounts.add(volumeMount);
            }
            case S3 -> {
                envVars.addAll(optionMapper(storageSpec.s3StorageSpec())
                        .mapOption("TRUSTD_S3_BUCKET", TrustifySpec.S3StorageSpec::bucket)
                        .mapOption("TRUSTD_S3_REGION", TrustifySpec.S3StorageSpec::region)
                        .mapOption("TRUSTD_S3_ACCESS_KEY", TrustifySpec.S3StorageSpec::accessKey)
                        .mapOption("TRUSTD_S3_SECRET_KEY", TrustifySpec.S3StorageSpec::secretKey)
                        .getEnvVars()
                );
            }
        }

        config.allEnvVars.addAll(envVars);
    }

    private void configureOidc(Config config, Trustify cr) {
        Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> {
                    if (!oidcSpec.enabled()) {
                        return Optional.empty();
                    }

                    List<EnvVar> embeddedUIEnvVars;
                    if (oidcSpec.externalServer()) {
                        embeddedUIEnvVars = optionMapper(oidcSpec.externalOidcSpec())
                                .mapOption("UI_ISSUER_URL", TrustifySpec.ExternalOidcSpec::serverUrl)
                                .mapOption("UI_CLIENT_ID", TrustifySpec.ExternalOidcSpec::uiClientId)
                                .getEnvVars();
                    } else {
                        embeddedUIEnvVars = optionMapper(oidcSpec.embeddedOidcSpec())
//                                .mapOption("UI_ISSUER_URL", embeddedOidcSpec -> AppIngress.getHostname(cr))
                                .mapOption("UI_CLIENT_ID", embeddedOidcSpec -> KeycloakRealmService.getUIClientName(cr))
                                .getEnvVars();
                    }
                    config.allEnvVars.addAll(embeddedUIEnvVars);


                    var authYaml = "/etc/config/auth.yaml";
                    var authVolume = new VolumeBuilder()
                            .withName("auth-pvol")
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
                    config.allVolumes.add(authVolume);
                    config.allVolumeMounts.add(authVolumeMount);

                    config.allEnvVars.add(new EnvVarBuilder()
                            .withName("AUTH_CONFIGURATION")
                            .withValue(authYaml)
                            .build()
                    );

                    config.allEnvVars.add(new EnvVarBuilder()
                            .withName("AUTH_DISABLED")
                            .withValue(Boolean.FALSE.toString())
                            .build()
                    );

                    return Optional.of(true);
                })
                .orElseGet(() -> {
                    config.allEnvVars.add(new EnvVarBuilder()
                            .withName("AUTH_DISABLED")
                            .withValue(Boolean.TRUE.toString())
                            .build()
                    );
                    return true;
                });
    }

    private <T> OptionMapper<T> optionMapper(T optionSpec) {
        return new OptionMapper<>(optionSpec);
    }

    private static class OptionMapper<T> {
        private final T categorySpec;
        private final List<EnvVar> envVars;

        public OptionMapper(T optionSpec) {
            this.categorySpec = optionSpec;
            this.envVars = new ArrayList<>();
        }

        public List<EnvVar> getEnvVars() {
            return envVars;
        }

        public <R> OptionMapper<T> mapOption(String optionName, Function<T, R> optionValueSupplier) {
            if (categorySpec == null) {
                Log.debugf("No category spec provided for %s", optionName);
                return this;
            }

            R value = optionValueSupplier.apply(categorySpec);

            if (value == null || value.toString().trim().isEmpty()) {
                Log.debugf("No value provided for %s", optionName);
                return this;
            }

            EnvVarBuilder envVarBuilder = new EnvVarBuilder()
                    .withName(optionName);

            if (value instanceof SecretKeySelector) {
                envVarBuilder.withValueFrom(new EnvVarSourceBuilder().withSecretKeyRef((SecretKeySelector) value).build());
            } else {
                envVarBuilder.withValue(String.valueOf(value));
            }

            envVars.add(envVarBuilder.build());

            return this;
        }

        public <R> OptionMapper<T> mapOption(String optionName) {
            return mapOption(optionName, s -> null);
        }

        public <R> OptionMapper<T> mapOption(String optionName, R optionValue) {
            return mapOption(optionName, s -> optionValue);
        }

        protected <R extends Collection<?>> OptionMapper<T> mapOptionFromCollection(String optionName, Function<T, R> optionValueSupplier) {
            return mapOption(optionName, s -> {
                var value = optionValueSupplier.apply(s);
                if (value == null) return null;
                return value.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
            });
        }
    }

}
