package org.trustify.operator.cdrs.v2alpha1.server.db.deployment;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.TrustifyImagesConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.db.pvc.DBPersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.server.db.secret.DBSecret;
import org.trustify.operator.controllers.DeploymentConfigurator;
import org.trustify.operator.utils.CRDUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DBDeploymentConfigurator implements DeploymentConfigurator {

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    @Inject
    TrustifyConfig trustifyConfig;

    @Override
    public Config configureDeployment(Trustify cr, Context<Trustify> context) {
        String image = Optional.ofNullable(cr.getSpec().dbImage()).orElse(trustifyImagesConfig.dbImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());

        List<LocalObjectReference> imagePullSecrets = Optional.ofNullable(cr.getSpec().imagePullSecrets()).orElse(new ArrayList<>());

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = Optional.ofNullable(cr.getSpec().databaseSpec())
                .flatMap(databaseSpec -> Optional.ofNullable(databaseSpec.embeddedDatabaseSpec()))
                .map(TrustifySpec.EmbeddedDatabaseSpec::resourceLimits)
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

        configureEnvs(config, cr, context);
        configureVolumes(config, cr, context);

        return config;
    }

    private void configureVolumes(Config config, Trustify cr, Context<Trustify> context) {
        String volName = "db-pvol";
        config.allVolumes().add(new VolumeBuilder()
                .withName(volName)
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(DBPersistentVolumeClaim.getPersistentVolumeClaimName(cr))
                        .build()
                )
                .build()
        );
        config.allVolumeMounts().add(new VolumeMountBuilder()
                .withName(volName)
                .withMountPath("/var/lib/pgsql/data")
                .build()
        );
    }

    private void configureEnvs(Config config, Trustify cr, Context<Trustify> context) {
        List<EnvVar> envVars = Arrays.asList(
                new EnvVarBuilder()
                        .withName("POSTGRESQL_USER")
                        .withValueFrom(new EnvVarSourceBuilder()
                                .withSecretKeyRef(DBSecret.getUsernameSecretKeySelector(cr))
                                .build()
                        )
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_PASSWORD")
                        .withValueFrom(new EnvVarSourceBuilder()
                                .withSecretKeyRef(DBSecret.getPasswordSecretKeySelector(cr))
                                .build()
                        )
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_DATABASE")
                        .withValue(DBDeployment.getDatabaseName(cr))
                        .build()
        );
        config.allEnvVars().addAll(envVars);
    }

}
