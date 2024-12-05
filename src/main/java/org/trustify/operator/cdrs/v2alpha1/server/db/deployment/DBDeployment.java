package org.trustify.operator.cdrs.v2alpha1.server.db.deployment;

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
import org.trustify.operator.cdrs.v2alpha1.server.db.pvc.DBPersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.server.db.secret.DBSecret;
import org.trustify.operator.cdrs.v2alpha1.server.db.utils.DBUtils;
import org.trustify.operator.utils.CRDUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@KubernetesDependent(labelSelector = DBDeployment.LABEL_SELECTOR, resourceDiscriminator = DBDeploymentDiscriminator.class)
@ApplicationScoped
public class DBDeployment extends CRUDKubernetesDependentResource<Deployment, Trustify>
        implements Matcher<Deployment, Trustify>, Condition<Deployment, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=db";

    @Inject
    TrustifyImagesConfig trustifyImagesConfig;

    public DBDeployment() {
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
    public boolean isMet(DependentResource<Deployment, Trustify> dependentResource, Trustify cr, Context<Trustify> context) {
        return DBUtils.isDeploymentReady(dependentResource, cr, context);
    }

    private Deployment newDeployment(Trustify cr, Context<Trustify> context) {
        return new DeploymentBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getDeploymentName(cr), LABEL_SELECTOR, cr))
                        .addToLabels("app.openshift.io/runtime", "postgresql")
                        .build()
                )
                .withSpec(getDeploymentSpec(cr, context))
                .build();
    }

    private DeploymentSpec getDeploymentSpec(Trustify cr, Context<Trustify> context) {
        String image = Optional.ofNullable(cr.getSpec().dbImage()).orElse(trustifyImagesConfig.dbImage());
        String imagePullPolicy = Optional.ofNullable(cr.getSpec().imagePullPolicy()).orElse(trustifyImagesConfig.imagePullPolicy());

        TrustifySpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils
                .getValueFromSubSpec(cr.getSpec().databaseSpec(), TrustifySpec.DatabaseSpec::resourceLimits)
                .orElse(null);

        return new DeploymentSpecBuilder()
                .withStrategy(new DeploymentStrategyBuilder()
                        .withType("Recreate")
                        .build()
                )
                .withReplicas(1)
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(getPodSelectorLabels())
                        .build()
                )
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata()
                        .withLabels(getPodSelectorLabels())
                        .endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withRestartPolicy("Always")
                                .withTerminationGracePeriodSeconds(60L)
                                .withImagePullSecrets(cr.getSpec().imagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.TRUSTI_DB_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(getEnvVars(cr))
                                        .withPorts(new ContainerPortBuilder()
                                                .withName("tcp")
                                                .withProtocol(Constants.SERVICE_PROTOCOL)
                                                .withContainerPort(getDatabasePort(cr))
                                                .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/sh", "-c", "psql -U $POSTGRESQL_USER -d $POSTGRESQL_DATABASE -c 'SELECT 1'")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(10)
                                                .withTimeoutSeconds(10)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/sh", "-c", "psql -U $POSTGRESQL_USER -d $POSTGRESQL_DATABASE -c 'SELECT 1'")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(5)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withVolumeMounts(new VolumeMountBuilder()
                                                .withName("db-pvol")
                                                .withMountPath("/var/lib/pgsql/data")
                                                .build()
                                        )
                                        .withResources(new ResourceRequirementsBuilder()
                                                .withRequests(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuRequest).orElse("50m")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryRequest).orElse("64Mi"))
                                                ))
                                                .withLimits(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuLimit).orElse("1")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryLimit).orElse("0.5Gi"))
                                                ))
                                                .build()
                                        )
                                        .build()
                                )
                                .withVolumes(new VolumeBuilder()
                                        .withName("db-pvol")
                                        .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                                                .withClaimName(DBPersistentVolumeClaim.getPersistentVolumeClaimName(cr))
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
                        .withName("POSTGRESQL_USER")
                        .withValueFrom(new EnvVarSourceBuilder()
                                .withSecretKeyRef(getUsernameSecretKeySelector(cr))
                                .build()
                        )
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_PASSWORD")
                        .withValueFrom(new EnvVarSourceBuilder()
                                .withSecretKeyRef(getPasswordSecretKeySelector(cr))
                                .build()
                        )
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_DATABASE")
                        .withValue(getDatabaseName(cr))
                        .build()
        );
    }

    public static String getDeploymentName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.DB_DEPLOYMENT_SUFFIX;
    }

    public static Map<String, String> getPodSelectorLabels() {
        return Map.of(
                "trustify-operator/group", "db"
        );
    }

    public static SecretKeySelector getUsernameSecretKeySelector(Trustify cr) {
        return Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(TrustifySpec.DatabaseSpec::usernameSecret)
                .map(secret -> new SecretKeySelectorBuilder()
                        .withName(secret.getName())
                        .withKey(secret.getKey())
                        .withOptional(false)
                        .build()
                )
                .orElseGet(() -> new SecretKeySelectorBuilder()
                        .withName(DBSecret.getSecretName(cr))
                        .withKey(Constants.DB_SECRET_USERNAME)
                        .withOptional(false)
                        .build()
                );
    }

    public static SecretKeySelector getPasswordSecretKeySelector(Trustify cr) {
        return Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(TrustifySpec.DatabaseSpec::passwordSecret)
                .map(secret -> new SecretKeySelectorBuilder()
                        .withName(secret.getName())
                        .withKey(secret.getKey())
                        .withOptional(false)
                        .build()
                )
                .orElseGet(() -> new SecretKeySelectorBuilder()
                        .withName(DBSecret.getSecretName(cr))
                        .withKey(Constants.DB_SECRET_PASSWORD)
                        .withOptional(false)
                        .build()
                );
    }

    public static String getDatabaseName(Trustify cr) {
        return Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(TrustifySpec.DatabaseSpec::name)
                .orElse(Constants.DB_NAME);
    }

    public static Integer getDatabasePort(Trustify cr) {
        return Constants.DB_PORT;
    }
}
