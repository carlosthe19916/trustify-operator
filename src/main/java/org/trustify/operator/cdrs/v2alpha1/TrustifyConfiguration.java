package org.trustify.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.*;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.db.secret.DBSecret;
import org.trustify.operator.cdrs.v2alpha1.server.db.service.DBService;
import org.trustify.operator.cdrs.v2alpha1.server.pvc.ServerStoragePersistentVolumeClaim;
import org.trustify.operator.controllers.ResourceConfigurator;
import org.trustify.operator.utils.OptionMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TrustifyConfiguration {

    protected void configureDatabase(ResourceConfigurator.Config config, Trustify cr) {
        List<EnvVar> envVars = Optional.ofNullable(cr.getSpec().databaseSpec())
                .flatMap(databaseSpec -> {
                    if (databaseSpec.externalDatabase()) {
                        List<EnvVar> envs = new OptionMapper<>(databaseSpec.externalDatabaseSpec())
                                .mapOption("TRUSTD_DB_USER", TrustifySpec.ExternalDatabaseSpec::usernameSecret)
                                .mapOption("TRUSTD_DB_PASSWORD", TrustifySpec.ExternalDatabaseSpec::passwordSecret)
                                .mapOption("TRUSTD_DB_NAME", TrustifySpec.ExternalDatabaseSpec::name)
                                .mapOption("TRUSTD_DB_HOST", TrustifySpec.ExternalDatabaseSpec::host)
                                .mapOption("TRUSTD_DB_PORT", TrustifySpec.ExternalDatabaseSpec::port)
                                .mapOption("TRUSTD_DB_MIN_CONN", TrustifySpec.ExternalDatabaseSpec::poolMinSize)
                                .mapOption("TRUSTD_DB_MAX_CONN", TrustifySpec.ExternalDatabaseSpec::poolMaxSize)
                                .mapOption("TRUSTD_DB_SSLMODE", TrustifySpec.ExternalDatabaseSpec::sslMode)
                                .getEnvVars();
                        return Optional.of(envs);
                    } else {
                        return Optional.empty();
                    }
                })
                .orElseGet(() -> new OptionMapper<>(cr.getSpec())
                        .mapOption("TRUSTD_DB_USER", spec -> DBSecret.getUsernameSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_PASSWORD", spec -> DBSecret.getPasswordSecretKeySelector(cr))
                        .mapOption("TRUSTD_DB_NAME", spec -> DBDeployment.getDatabaseName(cr))
                        .mapOption("TRUSTD_DB_HOST", spec -> DBService.getServiceHost(cr))
                        .mapOption("TRUSTD_DB_PORT", spec -> DBDeployment.getDatabasePort(cr))
                        .getEnvVars()
                );

        config.allEnvVars().addAll(envVars);
    }

    protected void configureStorage(ResourceConfigurator.Config config, Trustify cr) {
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
        envVars.addAll(new OptionMapper<>(storageSpec)
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

                config.allVolumes().add(volume);
                config.allVolumeMounts().add(volumeMount);
            }
            case S3 -> {
                envVars.addAll(new OptionMapper<>(storageSpec.s3StorageSpec())
                        .mapOption("TRUSTD_S3_BUCKET", TrustifySpec.S3StorageSpec::bucket)
                        .mapOption("TRUSTD_S3_REGION", TrustifySpec.S3StorageSpec::region)
                        .mapOption("TRUSTD_S3_ACCESS_KEY", TrustifySpec.S3StorageSpec::accessKey)
                        .mapOption("TRUSTD_S3_SECRET_KEY", TrustifySpec.S3StorageSpec::secretKey)
                        .getEnvVars()
                );
            }
        }

        config.allEnvVars().addAll(envVars);
    }

}
