package org.trustify.operator.cdrs.v2alpha1.ui.deployment;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.TrustifyImagesConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.service.ServerService;
import org.trustify.operator.utils.CRDUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@KubernetesDependent(labelSelector = UIDeployment.LABEL_SELECTOR, resourceDiscriminator = UIDeploymentDiscriminator.class)
@ApplicationScoped
public class UIDeployment extends CRUDKubernetesDependentResource<Deployment, Trustify>
        implements Matcher<Deployment, Trustify>, Condition<Deployment, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=ui";

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    @Inject
    ServerService serverService;

    public UIDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Trustify cr, Context<Trustify> context) {
        return newDeployment(cr, context);
    }

    @Override
    public Result<Deployment> match(Deployment actual, Trustify cr, Context<Trustify> context) {
        final var container = actual.getSpec()
                .getTemplate().getSpec().getContainers()
                .stream()
                .findFirst();

        return Result.nonComputed(container
                .map(c -> c.getImage() != null)
                .orElse(false)
        );
    }

    @Override
    public boolean isMet(DependentResource<Deployment, Trustify> dependentResource, Trustify primary, Context<Trustify> context) {
        return context.getSecondaryResource(Deployment.class, new UIDeploymentDiscriminator())
                .map(deployment -> {
                    final var status = deployment.getStatus();
                    if (status != null) {
                        final var readyReplicas = status.getReadyReplicas();
                        return readyReplicas != null && readyReplicas >= 1;
                    }
                    return false;
                })
                .orElse(false);
    }

    private Deployment newDeployment(Trustify cr, Context<Trustify> context) {
        return new DeploymentBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getDeploymentName(cr), LABEL_SELECTOR, cr))
                        .addToLabels(Map.of(
                                "app.openshift.io/runtime", "nodejs"
                        ))
                        .withAnnotations(Map.of("app.openshift.io/connects-to", """
                                [{"apiVersion": "apps/v1", "kind":"Deployment", "name": "%s"}]
                                """.formatted(ServerDeployment.getDeploymentName(cr))
                        ))
                        .build()
                )
                .withSpec(getDeploymentSpec(cr, context))
                .build();
    }

    private DeploymentSpec getDeploymentSpec(Trustify cr, Context<Trustify> context) {
        String image = Optional.ofNullable(cr.getSpec().uiImage()).orElse(trustifyImagesConfig.uiImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), TrustifySpec::uiResourceLimitSpec)
                .orElse(null);

        return new DeploymentSpecBuilder()
                .withStrategy(new DeploymentStrategyBuilder()
                        .withType("Recreate")
                        .build()
                )
                .withReplicas(1)
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(getPodSelectorLabels(cr))
                        .build()
                )
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata()
                        .withLabels(getPodSelectorLabels(cr))
                        .endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withRestartPolicy("Always")
                                .withTerminationGracePeriodSeconds(60L)
                                .withImagePullSecrets(cr.getSpec().imagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.TRUSTI_UI_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(getEnvVars(cr))
                                        .withPorts(
                                                new ContainerPortBuilder()
                                                        .withName("http")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(getDeploymentPort(cr))
                                                        .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand(
                                                                "/bin/sh",
                                                                "-c",
                                                                "ps -A | grep node"
                                                        )
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(10)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(5)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("/")
                                                        .withNewPort(getDeploymentPort(cr))
                                                        .withScheme("HTTP")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(10)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(5)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withResources(new ResourceRequirementsBuilder()
                                                .withRequests(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuRequest).orElse("100m")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryRequest).orElse("350Mi"))
                                                ))
                                                .withLimits(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuLimit).orElse("500m")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryLimit).orElse("800Mi"))
                                                ))
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();
    }

    private List<EnvVar> getEnvVars(Trustify cr) {
        return Arrays.asList(
                new EnvVarBuilder()
                        .withName("AUTH_REQUIRED")
                        .withValue("false")
                        .build(),
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
    }

    public static String getDeploymentName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.UI_DEPLOYMENT_SUFFIX;
    }

    public static int getDeploymentPort(Trustify cr) {
        return 8080;
    }

    public static Map<String, String> getPodSelectorLabels(Trustify cr) {
        return Map.of(
                "trustify-operator/group", "ui"
        );
    }
}
