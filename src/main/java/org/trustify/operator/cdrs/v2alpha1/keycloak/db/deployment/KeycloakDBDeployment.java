package org.trustify.operator.cdrs.v2alpha1.keycloak.db.deployment;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.controllers.DeploymentConfigurator;
import org.trustify.operator.services.KeycloakServerService;

import java.util.Map;

@KubernetesDependent(labelSelector = KeycloakDBDeployment.LABEL_SELECTOR, resourceDiscriminator = KeycloakDBDeploymentDiscriminator.class)
@ApplicationScoped
public class KeycloakDBDeployment extends CRUDKubernetesDependentResource<Deployment, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=keycloak";

    @Inject
    KeycloakDBDeploymentConfigurator keycloakDBDeploymentConfigurator;

    public KeycloakDBDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Trustify cr, Context<Trustify> context) {
        return newDeployment(cr, context);
    }

    @Override
    public Result<Deployment> match(Deployment actual, Trustify cr, Context<Trustify> context) {
        DeploymentConfigurator.Config config = keycloakDBDeploymentConfigurator.configureDeployment(cr, context);
        boolean match = config.match(actual.getSpec().getTemplate().getSpec());
        return Result.nonComputed(match);
    }

    private Deployment newDeployment(Trustify cr, Context<Trustify> context) {
        return new DeploymentBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getDeploymentName(cr), LABEL_SELECTOR, cr))
                        .addToLabels("app.openshift.io/runtime", "postgresql")
                        .withAnnotations(Map.of("app.openshift.io/connects-to", """
                                [{"apiVersion": "apps/v1", "kind":"StatefulSet", "name": "%s"}]
                                """.formatted(KeycloakServerService.getKeycloakName(cr))
                        ))
                        .build()
                )
                .withSpec(getDeploymentSpec(cr, context))
                .build();
    }

    private DeploymentSpec getDeploymentSpec(Trustify cr, Context<Trustify> context) {
        DeploymentConfigurator.Config config = keycloakDBDeploymentConfigurator.configureDeployment(cr, context);

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
                                .withImagePullSecrets(config.imagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName("database")
                                        .withImage(config.image())
                                        .withImagePullPolicy(config.imagePullPolicy())
                                        .withEnv(config.allEnvVars())
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
                                        .withVolumeMounts(config.allVolumeMounts())
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

    public static String getDeploymentName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.OIDC_DB_DEPLOYMENT_SUFFIX;
    }

    public static Map<String, String> getPodSelectorLabels(Trustify cr) {
        return Map.of(
                "trustify-operator/group", "keycloak"
        );
    }

    public static String getDatabaseName(Trustify cr) {
        return "keycloak";
    }

    public static Integer getDatabasePort(Trustify cr) {
        return Constants.DB_PORT;
    }

}
