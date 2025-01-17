package org.trustify.operator.cdrs.v2alpha1.keycloak.db.deployment;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.TrustifyImagesConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.pvc.KeycloakDBPersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.secret.KeycloakDBSecret;
import org.trustify.operator.controllers.ResourceConfigurator;
import org.trustify.operator.utils.CRDUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class KeycloakDBDeploymentConfigurator implements ResourceConfigurator {

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    @Inject
    TrustifyConfig trustifyConfig;

    @Override
    public Config configureDeployment(Trustify cr, Context<Trustify> context) {
        String image = Optional.ofNullable(cr.getSpec().dbImage()).orElse(trustifyImagesConfig.dbImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());

        List<LocalObjectReference> imagePullSecrets = Optional.ofNullable(cr.getSpec().imagePullSecrets()).orElse(new ArrayList<>());

        TrustifySpec.EmbeddedDatabaseSpec databaseSpec = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> Optional.ofNullable(oidcSpec.embeddedOidcSpec()))
                .flatMap(embeddedOidcSpec -> Optional.ofNullable(embeddedOidcSpec.databaseSpec()))
                .flatMap(dbSpec -> Optional.ofNullable(dbSpec.embeddedDatabaseSpec()))
                .orElse(null);
        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(databaseSpec, TrustifySpec.EmbeddedDatabaseSpec::resourceLimits)
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
                        .withClaimName(KeycloakDBPersistentVolumeClaim.getPersistentVolumeClaimName(cr))
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
                                .withSecretKeyRef(KeycloakDBSecret.getUsernameKeySelector(cr))
                                .build()
                        )
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_PASSWORD")
                        .withValueFrom(new EnvVarSourceBuilder()
                                .withSecretKeyRef(KeycloakDBSecret.getPasswordKeySelector(cr))
                                .build()
                        )
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_DATABASE")
                        .withValue(KeycloakDBDeployment.getDatabaseName(cr))
                        .build()
        );
        config.allEnvVars().addAll(envVars);
    }

}
