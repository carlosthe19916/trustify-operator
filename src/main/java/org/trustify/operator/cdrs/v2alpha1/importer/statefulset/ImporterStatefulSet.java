package org.trustify.operator.cdrs.v2alpha1.importer.statefulset;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeploymentConfigurator;
import org.trustify.operator.controllers.ResourceConfigurator;

import java.util.Map;
import java.util.Optional;

@KubernetesDependent(labelSelector = ImporterStatefulSet.LABEL_SELECTOR, resourceDiscriminator = ImporterStatefulSetDiscriminator.class)
@ApplicationScoped
public class ImporterStatefulSet extends CRUDKubernetesDependentResource<StatefulSet, Trustify>
        implements Matcher<StatefulSet, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=importer";

    @Inject
    TrustifyConfig trustifyConfig;

    @Inject
    ImporterStatefulSetConfigurator importerConfigurator;

    public ImporterStatefulSet() {
        super(StatefulSet.class);
    }

    @Override
    protected StatefulSet desired(Trustify cr, Context<Trustify> context) {
        return newStatefulSet(cr, context);
    }

    @Override
    public Result<StatefulSet> match(StatefulSet actual, Trustify cr, Context<Trustify> context) {
        boolean matchDesiredInstances = getDesiredInstances(cr) == actual.getSpec().getReplicas();
        if (!matchDesiredInstances) {
            return Result.nonComputed(false);
        }

        ResourceConfigurator.Config config = importerConfigurator.configureDeployment(cr, context);
        boolean match = config.match(actual.getSpec().getTemplate().getSpec());
        return Result.nonComputed(match);
    }

    private int getDesiredInstances(Trustify cr) {
        return Optional.ofNullable(cr.getSpec().importerInstances())
                .orElse(1);
    }

    private StatefulSet newStatefulSet(Trustify cr, Context<Trustify> context) {
        return new StatefulSetBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getStatefulSetName(cr), LABEL_SELECTOR, cr))
                        .withAnnotations(Map.of("app.openshift.io/connects-to", """
                                [{"apiVersion": "apps/v1", "kind":"StatefulSet", "name": "%s"}]
                                """.formatted(DBDeployment.getDeploymentName(cr))
                        ))
                        .build()
                )
                .withSpec(getStatefulSetSpec(cr, context))
                .build();
    }

    private StatefulSetSpec getStatefulSetSpec(Trustify cr, Context<Trustify> context) {
        ServerDeploymentConfigurator.Config config = importerConfigurator.configureDeployment(cr, context);

        String pvcStorageSize = Optional.ofNullable(cr.getSpec().importerWorkdirPvcSize())
                .orElse(trustifyConfig.defaultPvcSize());

        String persistentVolumeClaimName = "workdir";

        var volumeMount = new VolumeMountBuilder()
                .withName(persistentVolumeClaimName)
                .withMountPath("/data/workdir")
                .build();

        return new StatefulSetSpecBuilder()
                .withUpdateStrategy(new StatefulSetUpdateStrategyBuilder()
                        .withType("RollingUpdate")
                        .build()
                )
                .withReplicas(getDesiredInstances(cr))
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(getPodSelectorLabels(cr))
                        .build()
                )
                .withVolumeClaimTemplates(new PersistentVolumeClaimBuilder()
                        .withMetadata(new ObjectMetaBuilder()
                                .withName(persistentVolumeClaimName)
                                .build()
                        )
                        .withSpec(new PersistentVolumeClaimSpecBuilder()
                                .withAccessModes("ReadWriteOnce")
                                .withResources(new VolumeResourceRequirementsBuilder()
                                        .withRequests(Map.of("storage", new Quantity(pvcStorageSize)))
                                        .build()
                                )
                                .build()
                        )
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
                                .withContainers(new ContainerBuilder()
                                        .withName("importer")
                                        .withImage(config.image())
                                        .withImagePullPolicy(config.imagePullPolicy())
                                        .withEnv(config.allEnvVars())
                                        .withCommand("/usr/local/bin/trustd")
                                        .withArgs(
                                                "importer",
                                                "--working-dir",
                                                volumeMount.getMountPath()
                                        )
                                        .withPorts(
                                                new ContainerPortBuilder()
                                                        .withName("http-infra")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(getStatefulSetInfrastructurePort(cr))
                                                        .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("/health/live")
                                                        .withNewPort(getStatefulSetInfrastructurePort(cr))
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
                                                        .withNewPort(getStatefulSetInfrastructurePort(cr))
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
                                                        .withNewPort(getStatefulSetInfrastructurePort(cr))
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
                                        .withVolumeMounts(config.allVolumeMounts())
                                        .addToVolumeMounts(volumeMount)
                                        .withResources(config.resourceRequirements())
                                        .build()
                                )
                                .withVolumes(config.allVolumes())
                                .build()
                        )
                        .build()
                )
                .build();
    }

    public static String getStatefulSetName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.IMPORTER_STATEFUL_SET_SUFFIX;
    }

    public static int getStatefulSetInfrastructurePort(Trustify cr) {
        return 9010;
    }

    public static Map<String, String> getPodSelectorLabels(Trustify cr) {
        return Map.of(
                "trustify-operator/group", "importer"
        );
    }
}
