package org.trustify.operator.cdrs.v2alpha1.ui.deployment;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.keycloak.k8s.v2alpha1.Keycloak;
import org.trustify.operator.Constants;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.TrustifyImagesConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.service.ServerService;
import org.trustify.operator.controllers.DeploymentConfigurator;
import org.trustify.operator.services.KeycloakRealmService;
import org.trustify.operator.services.KeycloakServerService;
import org.trustify.operator.utils.CRDUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class UIDeploymentConfigurator implements DeploymentConfigurator {

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    @Inject
    TrustifyConfig trustifyConfig;

    @Inject
    ServerService serverService;

    @Override
    public Config configureDeployment(Trustify cr, Context<Trustify> context) {
        String image = Optional.ofNullable(cr.getSpec().uiImage()).orElse(trustifyImagesConfig.uiImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());
        List<LocalObjectReference> imagePullSecrets = Optional.ofNullable(cr.getSpec().imagePullSecrets()).orElse(new ArrayList<>());

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = cr.getSpec().uiResourceLimitSpec();
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

        return config;
    }

    private void configureEnvs(Config config, Trustify cr, Context<Trustify> context) {
        List<EnvVar> oidcEnvVars = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> {
                    if (!oidcSpec.enabled()) {
                        return Optional.empty();
                    }

                    List<EnvVar> envVars;
                    if (oidcSpec.externalServer()) {
                        envVars = Optional.ofNullable(oidcSpec.externalOidcSpec())
                                .map(externalOidcSpec -> List.of(
                                        new EnvVarBuilder()
                                                .withName("OIDC_SERVER_URL")
                                                .withValue(externalOidcSpec.serverUrl())
                                                .build(),
                                        new EnvVarBuilder()
                                                .withName("OIDC_CLIENT_ID")
                                                .withValue(externalOidcSpec.uiClientId())
                                                .build()
                                ))
                                .orElseGet(ArrayList::new);
                    } else {
                        final AtomicReference<Keycloak> keycloakInstance = context.managedDependentResourceContext().getMandatory(Constants.KEYCLOAK, AtomicReference.class);
                        envVars = List.of(
                                new EnvVarBuilder()
                                        .withName("OIDC_SERVER_URL")
                                        .withValue(KeycloakServerService.getServiceUrl(cr, keycloakInstance.get()))
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("OIDC_CLIENT_ID")
                                        .withValue(KeycloakRealmService.getUIClientName(cr))
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("OIDC_SERVER_IS_EMBEDDED")
                                        .withValue(Boolean.TRUE.toString())
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("OIDC_SERVER_EMBEDDED_PATH")
                                        .withValue(KeycloakRealmService.getRealmClientRelativePath(cr))
                                        .build()
                        );
                    }

                    List<EnvVar> result = new ArrayList<>();
                    result.add(new EnvVarBuilder()
                            .withName("AUTH_REQUIRED")
                            .withValue(Boolean.TRUE.toString())
                            .build()
                    );
                    result.addAll(envVars);
                    return Optional.of(result);
                })
                .orElseGet(() -> List.of(new EnvVarBuilder()
                        .withName("AUTH_REQUIRED")
                        .withValue(Boolean.FALSE.toString())
                        .build()
                ));

        List<EnvVar> envVars = Arrays.asList(
                new EnvVarBuilder()
                        .withName("ANALYTICS_ENABLED")
                        .withValue("false")
                        .build(),
                new EnvVarBuilder()
                        .withName("TRUSTIFY_API_URL")
                        .withValue(serverService.getServiceUrl(cr))
                        .build(),
                new EnvVarBuilder()
                        .withName("UI_INGRESS_PROXY_BODY_SIZE")
                        .withValue("50m")
                        .build(),
                new EnvVarBuilder()
                        .withName("NODE_EXTRA_CA_CERTS")
                        .withValue("/opt/app-root/src/ca.crt")
                        .build()
        );

        config.allEnvVars().addAll(oidcEnvVars);
        config.allEnvVars().addAll(envVars);
    }

}
