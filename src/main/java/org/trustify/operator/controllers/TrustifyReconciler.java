package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifyStatusCondition;
import org.trustify.operator.cdrs.v2alpha1.ingress.AppIngress;
import org.trustify.operator.cdrs.v2alpha1.ingress.AppIngressReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeploymentActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeploymentReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.server.db.pvc.DBPersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.server.db.pvc.DBPersistentVolumeClaimActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.server.db.secret.DBSecret;
import org.trustify.operator.cdrs.v2alpha1.server.db.secret.DBSecretActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.server.db.service.DBService;
import org.trustify.operator.cdrs.v2alpha1.server.db.service.DBServiceActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeploymentReconcilePreCondition;
import org.trustify.operator.cdrs.v2alpha1.server.pvc.ServerStoragePersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.server.pvc.ServerStoragePersistentVolumeClaimActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.server.service.ServerService;
import org.trustify.operator.cdrs.v2alpha1.ui.deployment.UIDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.service.UIService;
import org.trustify.operator.services.ClusterService;

import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "trustify",
        dependents = {
                @Dependent(
                        name = "db-pvc",
                        type = DBPersistentVolumeClaim.class,
                        activationCondition = DBPersistentVolumeClaimActivationCondition.class
                ),
                @Dependent(
                        name = "db-secret",
                        type = DBSecret.class,
                        activationCondition = DBSecretActivationCondition.class
                ),
                @Dependent(
                        name = "db-deployment",
                        type = DBDeployment.class,
                        dependsOn = {"db-pvc", "db-secret"},
                        readyPostcondition = DBDeploymentReadyPostCondition.class,
                        activationCondition = DBDeploymentActivationCondition.class
                ),
                @Dependent(
                        name = "db-service",
                        type = DBService.class,
                        activationCondition = DBServiceActivationCondition.class
                ),

                @Dependent(
                        name = "server-pvc",
                        type = ServerStoragePersistentVolumeClaim.class,
                        activationCondition = ServerStoragePersistentVolumeClaimActivationCondition.class
                ),
                @Dependent(
                        name = "server-deployment",
                        type = ServerDeployment.class,
                        reconcilePrecondition = ServerDeploymentReconcilePreCondition.class,
                        readyPostcondition = ServerDeployment.class
                ),
                @Dependent(
                        name = "server-service",
                        type = ServerService.class
                ),

                @Dependent(
                        name = "ui-deployment",
                        type = UIDeployment.class,
                        readyPostcondition = UIDeployment.class
                ),
                @Dependent(
                        name = "ui-service",
                        type = UIService.class
                ),

                @Dependent(
                        name = "app-ingress",
                        type = AppIngress.class,
                        readyPostcondition = AppIngressReadyPostCondition.class
                )
        }
)
public class TrustifyReconciler implements Reconciler<Trustify>, ContextInitializer<Trustify>, EventSourceInitializer<Trustify> {

    private static final Logger logger = Logger.getLogger(TrustifyReconciler.class);

    public static final String CONFIG_MAP_EVENT_SOURCE = "configMapSource";
    public static final String PVC_EVENT_SOURCE = "pcvSource";
    public static final String SECRET_EVENT_SOURCE = "secretSource";
    public static final String DEPLOYMENT_EVENT_SOURCE = "deploymentSource";
    public static final String SERVICE_EVENT_SOURCE = "serviceSource";

    @Inject
    ClusterService clusterService;

    @Override
    public void initContext(Trustify cr, Context<Trustify> context) {
        context.managedDependentResourceContext().put(Constants.CLUSTER_SERVICE, clusterService);
    }

    @Override
    public UpdateControl<Trustify> reconcile(Trustify cr, Context context) {
        return context.managedDependentResourceContext()
                .getWorkflowReconcileResult()
                .map(wrs -> {
                    if (wrs.allDependentResourcesReady()) {
                        if (cr.getStatus().isAvailable()) {
                            logger.infof("Trustify %s is ready to be used", cr.getMetadata().getName());
                        }

                        TrustifyStatusCondition status = new TrustifyStatusCondition();
                        status.setType(TrustifyStatusCondition.SUCCESSFUL);
                        status.setStatus(true);

                        cr.getStatus().setCondition(status);

                        return UpdateControl.updateStatus(cr);
                    } else {
                        TrustifyStatusCondition status = new TrustifyStatusCondition();
                        status.setType(TrustifyStatusCondition.PROCESSING);
                        status.setStatus(true);

                        cr.getStatus().setCondition(status);

                        final var duration = Duration.ofSeconds(5);
                        return UpdateControl.updateStatus(cr).rescheduleAfter(duration);
                    }
                })
                .orElseThrow();
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<Trustify> context) {
        var configMapInformerConfiguration = InformerConfiguration.from(ConfigMap.class, context).build();
        var pcvInformerConfiguration = InformerConfiguration.from(PersistentVolumeClaim.class, context).build();
        var secretInformerConfiguration = InformerConfiguration.from(Secret.class, context).build();
        var deploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context).build();
        var serviceInformerConfiguration = InformerConfiguration.from(Service.class, context).build();

        var configMapInformerConfigurationInformerEventSource = new InformerEventSource<>(configMapInformerConfiguration, context);
        var pcvInformerEventSource = new InformerEventSource<>(pcvInformerConfiguration, context);
        var secretInformerEventSource = new InformerEventSource<>(secretInformerConfiguration, context);
        var deploymentInformerEventSource = new InformerEventSource<>(deploymentInformerConfiguration, context);
        var serviceInformerEventSource = new InformerEventSource<>(serviceInformerConfiguration, context);

        return Map.of(
                CONFIG_MAP_EVENT_SOURCE, configMapInformerConfigurationInformerEventSource,
                PVC_EVENT_SOURCE, pcvInformerEventSource,
                SECRET_EVENT_SOURCE, secretInformerEventSource,
                DEPLOYMENT_EVENT_SOURCE, deploymentInformerEventSource,
                SERVICE_EVENT_SOURCE, serviceInformerEventSource
        );
    }
}
