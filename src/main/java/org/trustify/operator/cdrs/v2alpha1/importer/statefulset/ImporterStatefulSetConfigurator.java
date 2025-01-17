package org.trustify.operator.cdrs.v2alpha1.importer.statefulset;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.TrustifyImagesConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifyConfiguration;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.controllers.ResourceConfigurator;
import org.trustify.operator.utils.CRDUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ImporterStatefulSetConfigurator extends TrustifyConfiguration implements ResourceConfigurator {

    @Inject
    TrustifyConfig trustifyConfig;

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    @Override
    public Config configureDeployment(Trustify cr, Context<Trustify> context) {
        String image = Optional.ofNullable(cr.getSpec().serverImage()).orElse(trustifyImagesConfig.serverImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());

        List<LocalObjectReference> imagePullSecrets = Optional.ofNullable(cr.getSpec().imagePullSecrets()).orElse(new ArrayList<>());

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), TrustifySpec::importerResourceLimitSpec)
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
        configureDatabase(config, cr);
        configureStorage(config, cr);

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
                .withValue("[::]:" + ImporterStatefulSet.getStatefulSetInfrastructurePort(cr))
                .build()
        );
        config.allEnvVars().add(new EnvVarBuilder()
                .withName("CLIENT_TLS_CA_CERTIFICATES")
                .withValue("/run/secrets/kubernetes.io/serviceaccount/service-ca.crt")
                .build()
        );
    }

}
