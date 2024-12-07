package org.trustify.operator.cdrs.v2alpha1.server.deployment;

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
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.TrustifyImagesConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;
import org.trustify.operator.controllers.TrustifyDistConfigurator;
import org.trustify.operator.utils.CRDUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@KubernetesDependent(labelSelector = ServerDeployment.LABEL_SELECTOR, resourceDiscriminator = ServerDeploymentDiscriminator.class)
@ApplicationScoped
public class ServerDeployment extends CRUDKubernetesDependentResource<Deployment, Trustify>
        implements Matcher<Deployment, Trustify>, Condition<Deployment, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    @Inject
    TrustifyConfig trustifyConfig;

    @Inject
    TrustifyDistConfigurator distConfigurator;

    public ServerDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Trustify cr, Context<Trustify> context) {
        return newDeployment(cr, context, distConfigurator);
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
        return context.getSecondaryResource(Deployment.class, new ServerDeploymentDiscriminator())
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

    private Deployment newDeployment(Trustify cr, Context<Trustify> context, TrustifyDistConfigurator distConfigurator) {
        return new DeploymentBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getDeploymentName(cr), LABEL_SELECTOR, cr))
                        .withAnnotations(Map.of("app.openshift.io/connects-to", """
                                [{"apiVersion": "apps/v1", "kind":"Deployment", "name": "%s"}]
                                """.formatted(DBDeployment.getDeploymentName(cr))
                        ))
                        .build()
                )
                .withSpec(getDeploymentSpec(cr, context, distConfigurator))
                .build();
    }

    private DeploymentSpec getDeploymentSpec(Trustify cr, Context<Trustify> context, TrustifyDistConfigurator distConfigurator) {
        String image = Optional.ofNullable(cr.getSpec().serverImage()).orElse(trustifyImagesConfig.serverImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());

        TrustifyDistConfigurator.Config distConfig = distConfigurator.configureDistOption(cr);
        List<EnvVar> envVars = distConfig.allEnvVars();
        List<Volume> volumes = distConfig.allVolumes();
        List<VolumeMount> volumeMounts = distConfig.allVolumeMounts();

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), TrustifySpec::serverResourceLimitSpec)
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
                        .addToLabels(getPodSelectorLabels(cr))
                        .endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withRestartPolicy("Always")
                                .withTerminationGracePeriodSeconds(70L)
                                .withImagePullSecrets(cr.getSpec().imagePullSecrets())
                                .withInitContainers(new ContainerBuilder()
                                        .withName("migrate")
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(envVars)
                                        .withCommand("/usr/local/bin/trustd")
                                        .withArgs(
                                                "db",
                                                "migrate"
                                        )
                                        .build()
                                )
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.TRUSTI_SERVER_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(envVars)
                                        .withCommand("/usr/local/bin/trustd")
                                        .withArgs(
                                                "api",
                                                "--sample-data"
                                        )
                                        .withPorts(
                                                new ContainerPortBuilder()
                                                        .withName("http")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(getDeploymentPort(cr))
                                                        .build(),
                                                new ContainerPortBuilder()
                                                        .withName("http-infra")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(getDeploymentInfrastructurePort(cr))
                                                        .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("/health/live")
                                                        .withNewPort(getDeploymentInfrastructurePort(cr))
                                                        .withScheme("HTTP")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(5)
                                                .withTimeoutSeconds(10)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("health/ready")
                                                        .withNewPort(getDeploymentInfrastructurePort(cr))
                                                        .withScheme("HTTP")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(5)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withStartupProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("/health/startup")
                                                        .withNewPort(getDeploymentInfrastructurePort(cr))
                                                        .withScheme("HTTP")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(5)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withVolumeMounts(volumeMounts)
                                        .withResources(CRDUtils.getResourceRequirements(resourcesLimitSpec, trustifyConfig))
                                        .build()
                                )
                                .withVolumes(volumes)
                                .build()
                        )
                        .build()
                )
                .build();
    }

    public static String getDeploymentName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.SERVER_DEPLOYMENT_SUFFIX;
    }

    public static int getDeploymentPort(Trustify cr) {
        return 8080;
    }

    public static int getDeploymentInfrastructurePort(Trustify cr) {
        return 9010;
    }


    public static Map<String, String> getPodSelectorLabels(Trustify cr) {
        return Map.of(
                "trustify-operator/group", "server"
        );
    }
}
