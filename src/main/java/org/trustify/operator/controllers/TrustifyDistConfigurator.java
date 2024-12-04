package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.db.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.db.DBService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.KeycloakHttpTlsSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakRealmService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakServerService;
import org.trustify.operator.cdrs.v2alpha1.server.ServerConfigurationConfigMap;
import org.trustify.operator.cdrs.v2alpha1.server.ServerService;
import org.trustify.operator.cdrs.v2alpha1.server.ServerStoragePersistentVolumeClaim;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class TrustifyDistConfigurator {

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
                .withValue("[::]:" + Constants.HTTP_INFRAESTRUCTURE_PORT)
                .build()
        );
        config.allEnvVars.add(new EnvVarBuilder()
                .withName("CLIENT_TLS_CA_CERTIFICATES")
                .withValue("/run/secrets/kubernetes.io/serviceaccount/service-ca.crt")
                .build()
        );
    }

    private void configureHttp(Config config, Trustify cr) {
        var optionMapper = optionMapper(cr.getSpec().httpSpec());
        configureTLS(config, cr, optionMapper);

        List<EnvVar> envVars = optionMapper.getEnvVars();
        config.allEnvVars.addAll(envVars);

        // Force to use HTTP v4
        config.allEnvVars.add(new EnvVarBuilder()
                .withName("HTTP_SERVER_BIND_ADDR")
                .withValue("::")
                .build()
        );
    }

    private void configureTLS(Config config, Trustify cr, OptionMapper<TrustifySpec.HttpSpec> optionMapper) {
        final String certFileOptionName = "HTTP_SERVER_TLS_CERTIFICATE_FILE";
        final String keyFileOptionName = "HTTP_SERVER_TLS_KEY_FILE";

        if (!ServerService.isTlsConfigured(cr)) {
            // for mapping and triggering warning in status if someone uses the fields directly
            optionMapper.mapOption(certFileOptionName);
            optionMapper.mapOption(keyFileOptionName);
            return;
        }

        optionMapper.mapOption("HTTP_SERVER_TLS_ENABLED", httpSpec -> true);
        optionMapper.mapOption(certFileOptionName, Constants.CERTIFICATES_FOLDER + "/tls.crt");
        optionMapper.mapOption(keyFileOptionName, Constants.CERTIFICATES_FOLDER + "/tls.key");

        var volume = new VolumeBuilder()
                .withName("trustify-tls-certificates")
                .withNewSecret()
                .withSecretName(cr.getSpec().httpSpec().tlsSecret())
                .withOptional(false)
                .endSecret()
                .build();

        var volumeMount = new VolumeMountBuilder()
                .withName(volume.getName())
                .withMountPath(Constants.CERTIFICATES_FOLDER)
                .build();

        config.allVolumes.add(volume);
        config.allVolumeMounts().add(volumeMount);
    }

    private void configureDatabase(Config config, Trustify cr) {
        List<EnvVar> envVars = Optional.ofNullable(cr.getSpec().databaseSpec())
                .flatMap(databaseSpec -> {
                    if (databaseSpec.externalDatabase()) {
                        List<EnvVar> envs = optionMapper(cr.getSpec())
                                .mapOption("TRUSTD_DB_USER", spec -> databaseSpec.usernameSecret())
                                .mapOption("TRUSTD_DB_PASSWORD", spec -> databaseSpec.passwordSecret())
                                .mapOption("TRUSTD_DB_NAME", spec -> databaseSpec.name())
                                .mapOption("TRUSTD_DB_HOST", spec -> databaseSpec.host())
                                .mapOption("TRUSTD_DB_PORT", spec -> databaseSpec.port())
                                .getEnvVars();
                        return Optional.of(envs);
                    } else {
                        return Optional.empty();
                    }
                })
                .orElseGet(() -> optionMapper(cr.getSpec())
                        .mapOption("TRUSTD_DB_USER", spec -> DBDeployment.getUsernameSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_PASSWORD", spec -> DBDeployment.getPasswordSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_NAME", spec -> DBDeployment.getDatabaseName(cr))
                        .mapOption("TRUSTD_DB_HOST", spec -> String.format("%s.%s.svc", DBService.getServiceName(cr), cr.getMetadata().getNamespace()))
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
                .ifPresentOrElse((oidcSpec) -> {
                    if (oidcSpec.enabled()) {
                        // Volumes
                        var oidcTlsCertVolume = new VolumeBuilder()
                                .withName("trust-anchor")
                                .withSecret(new SecretVolumeSourceBuilder()
                                        .withSecretName(KeycloakHttpTlsSecret.getSecretName(cr))
                                        .build()
                                )
                                .build();
                        var oidcTlsCertVolumeMount = new VolumeMountBuilder()
                                .withName(oidcTlsCertVolume.getName())
                                .withMountPath(ServerConfigurationConfigMap.getConfigMapOidcTlsExpectedPath())
                                .withSubPath(KeycloakHttpTlsSecret.getSecretCrtFieldName())
                                .withReadOnly(true)
                                .build();
                        config.allVolumes.add(oidcTlsCertVolume);
                        config.allVolumeMounts.add(oidcTlsCertVolumeMount);

                        var authMountPath = "/etc/config/auth.yaml";
                        var configurationVolume = new VolumeBuilder()
                                .withName("config-auth")
                                .withConfigMap(new ConfigMapVolumeSourceBuilder()
                                        .withName(ServerConfigurationConfigMap.getConfigMapName(cr))
                                        .withDefaultMode(420)
                                        .build()
                                )
                                .build();
                        var configurationVolumeMount = new VolumeMountBuilder()
                                .withName(configurationVolume.getName())
                                .withMountPath(authMountPath)
                                .withSubPath(ServerConfigurationConfigMap.getConfigMapAuthKey(cr))
                                .withReadOnly(true)
                                .build();
                        config.allVolumes.add(configurationVolume);
                        config.allVolumeMounts.add(configurationVolumeMount);

                        // Env vars
                        TrustifySpec.OidcProviderType providerType = Objects.nonNull(oidcSpec.type()) ? oidcSpec.type() : TrustifySpec.OidcProviderType.EMBEDDED;

                        List<EnvVar> envVars;
                        switch (providerType) {
                            case EXTERNAL -> envVars = Optional.ofNullable(oidcSpec.externalOidcSpec())
                                    .map(externalOidcSpec -> optionMapper(externalOidcSpec)
                                            .mapOption("UI_ISSUER_URL", TrustifySpec.ExternalOidcSpec::serverUrl)
                                            .mapOption("UI_CLIENT_ID", TrustifySpec.ExternalOidcSpec::uiClientId)
                                            .getEnvVars()
                                    )
                                    .orElseGet(ArrayList::new);
                            case EMBEDDED -> envVars = List.of(
                                    new EnvVarBuilder()
                                            .withName("UI_ISSUER_URL")
                                            .withValue(KeycloakServerService.getServiceHostUrl(cr))
                                            .build(),
                                    new EnvVarBuilder()
                                            .withName("UI_CLIENT_ID")
                                            .withValue(KeycloakRealmService.getUIClientName(cr))
                                            .build()
                            );
                            default -> envVars = Collections.emptyList();
                        }

                        config.allEnvVars.add(new EnvVarBuilder()
                                .withName("AUTH_CONFIGURATION")
                                .withValue(authMountPath)
                                .build()
                        );
                        config.allEnvVars.add(new EnvVarBuilder()
                                .withName("AUTH_DISABLED")
                                .withValue(Boolean.FALSE.toString())
                                .build()
                        );
                        config.allEnvVars.addAll(envVars);
                    } else {
                        config.allEnvVars.add(new EnvVarBuilder()
                                .withName("AUTH_DISABLED")
                                .withValue(Boolean.TRUE.toString())
                                .build()
                        );
                    }
                }, () -> config.allEnvVars.add(new EnvVarBuilder()
                        .withName("AUTH_DISABLED")
                        .withValue(Boolean.TRUE.toString())
                        .build()
                ));
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
