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
import org.keycloak.k8s.v2alpha1.Keycloak;
import org.keycloak.k8s.v2alpha1.KeycloakRealmImport;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifyStatusCondition;
import org.trustify.operator.cdrs.v2alpha1.ingress.AppIngress;
import org.trustify.operator.cdrs.v2alpha1.ingress.AppIngressReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.deployment.KeycloakDBDeployment;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.deployment.KeycloakDBDeploymentActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.deployment.KeycloakDBDeploymentReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.pvc.KeycloakDBPersistentVolumeClaim;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.pvc.KeycloakDBPersistentVolumeClaimActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.secret.KeycloakDBSecret;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.secret.KeycloakDBSecretActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.service.KeycloakDBService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.service.KeycloakDBServiceActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.service.KeycloakTlsService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.service.KeycloakTlsServiceActivationCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.service.KeycloakTlsServiceReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;
import org.trustify.operator.cdrs.v2alpha1.server.configmap.ServerConfigMap;
import org.trustify.operator.cdrs.v2alpha1.server.configmap.ServerConfigMapReconcilePreCondition;
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
import org.trustify.operator.cdrs.v2alpha1.server.service.ServerServiceReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.ui.deployment.UIDeployment;
import org.trustify.operator.cdrs.v2alpha1.ui.service.UIService;
import org.trustify.operator.services.ClusterService;
import org.trustify.operator.services.KeycloakOperatorService;
import org.trustify.operator.services.KeycloakRealmService;
import org.trustify.operator.services.KeycloakServerService;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "trustify",
        dependents = {
                @Dependent(
                        name = "keycloak-tls",
                        type = KeycloakTlsService.class,
                        activationCondition = KeycloakTlsServiceActivationCondition.class,
                        readyPostcondition = KeycloakTlsServiceReadyPostCondition.class
                ),

                @Dependent(
                        name = "keycloak-db-pvc",
                        type = KeycloakDBPersistentVolumeClaim.class,
                        activationCondition = KeycloakDBPersistentVolumeClaimActivationCondition.class
                ),
                @Dependent(
                        name = "keycloak-db-secret",
                        type = KeycloakDBSecret.class,
                        activationCondition = KeycloakDBSecretActivationCondition.class
                ),
                @Dependent(
                        name = "keycloak-db-deployment",
                        type = KeycloakDBDeployment.class,
                        dependsOn = {"keycloak-db-pvc", "keycloak-db-secret"},
                        activationCondition = KeycloakDBDeploymentActivationCondition.class,
                        readyPostcondition = KeycloakDBDeploymentReadyPostCondition.class
                ),
                @Dependent(
                        name = "keycloak-db-service",
                        type = KeycloakDBService.class,
                        activationCondition = KeycloakDBServiceActivationCondition.class
                ),

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
                        activationCondition = DBDeploymentActivationCondition.class,
                        readyPostcondition = DBDeploymentReadyPostCondition.class
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
                        name = "server-configmap",
                        type = ServerConfigMap.class,
                        reconcilePrecondition = ServerConfigMapReconcilePreCondition.class
                ),
                @Dependent(
                        name = "server-deployment",
                        type = ServerDeployment.class,
                        dependsOn = {"server-configmap", "server-service"},
                        reconcilePrecondition = ServerDeploymentReconcilePreCondition.class,
                        readyPostcondition = ServerDeployment.class
                ),
                @Dependent(
                        name = "server-service",
                        type = ServerService.class,
                        readyPostcondition = ServerServiceReadyPostCondition.class
                ),

                @Dependent(
                        name = "ui-deployment",
                        type = UIDeployment.class,
                        dependsOn = {"server-deployment"},
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
public class TrustifyReconciler implements Reconciler<Trustify>, Cleaner<Trustify>, ContextInitializer<Trustify>, EventSourceInitializer<Trustify> {

    private static final Logger logger = Logger.getLogger(TrustifyReconciler.class);

    public static final String CONFIG_MAP_EVENT_SOURCE = "configMapSource";
    public static final String PVC_EVENT_SOURCE = "pcvSource";
    public static final String SECRET_EVENT_SOURCE = "secretSource";
    public static final String DEPLOYMENT_EVENT_SOURCE = "deploymentSource";
    public static final String SERVICE_EVENT_SOURCE = "serviceSource";

    @Inject
    ClusterService clusterService;

    @Inject
    KeycloakOperatorService keycloakOperatorService;

    @Inject
    KeycloakServerService keycloakServerService;

    @Inject
    KeycloakRealmService keycloakRealmService;

    AtomicReference<Keycloak> keycloakInstance = new AtomicReference<>();
    AtomicReference<KeycloakRealmImport> keycloakRealmImportInstance = new AtomicReference<>();

    @Override
    public void initContext(Trustify cr, Context<Trustify> context) {
        context.managedDependentResourceContext().put(Constants.CLUSTER_SERVICE, clusterService);
        context.managedDependentResourceContext().put(Constants.CONTEXT_KEYCLOAK_SERVER_SERVICE_KEY, keycloakServerService);
        context.managedDependentResourceContext().put(Constants.CONTEXT_KEYCLOAK_REALM_SERVICE_KEY, keycloakRealmService);

        context.managedDependentResourceContext().put(Constants.KEYCLOAK, keycloakInstance);
        context.managedDependentResourceContext().put(Constants.KEYCLOAK_REALM_IMPORT, keycloakRealmImportInstance);
    }

    @Override
    public UpdateControl<Trustify> reconcile(Trustify cr, Context<Trustify> context) {
        Optional<UpdateControl<Trustify>> kcUpdateControl = createOrUpdateKeycloakResources(cr, context);
        if (kcUpdateControl.isPresent()) {
            return kcUpdateControl.get();
        }

        return createOrUpdateDependantResources(cr, context);
    }

    private Optional<UpdateControl<Trustify>> createOrUpdateKeycloakResources(Trustify cr, Context<Trustify> context) {
        boolean isKcRequired = KeycloakUtils.isKeycloakRequired(cr);
        if (isKcRequired) {
            // Keycloak Operator
            boolean kcSubscriptionExists = keycloakOperatorService.subscriptionExists(cr);
            if (!kcSubscriptionExists) {
                logger.info("Installing Keycloak Operator");
                keycloakOperatorService.createSubscription(cr);
            }

            AbstractMap.SimpleEntry<Boolean, String> subscriptionReady = keycloakOperatorService.isSubscriptionReady(cr);
            if (!subscriptionReady.getKey()) {
                logger.infof("Waiting for the Keycloak Operator to be ready: %s", subscriptionReady.getValue());
                return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
            }

            // Keycloak dependencies
            KeycloakTlsServiceActivationCondition keycloakActivationCondition = new KeycloakTlsServiceActivationCondition();
            boolean isKeycloakTlsEnabled = keycloakActivationCondition.isMet(null, cr, context);
            if (isKeycloakTlsEnabled) {
                KeycloakTlsServiceReadyPostCondition keycloakReadyCondition = new KeycloakTlsServiceReadyPostCondition();
                boolean isKeycloakTlsReady = keycloakReadyCondition.isMet(null, cr, context);
                if (!isKeycloakTlsReady) {
                    logger.info("Waiting for the TLS Secrets for Keycloak to be created");
                    return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
                }
            }

            KeycloakDBDeploymentActivationCondition keycloakDBActivationCondition = new KeycloakDBDeploymentActivationCondition();
            boolean isKeycloakDBEnabled = keycloakDBActivationCondition.isMet(null, cr, context);
            if (isKeycloakDBEnabled) {
                KeycloakDBDeploymentReadyPostCondition keycloakDBDeploymentReadyCondition = new KeycloakDBDeploymentReadyPostCondition();
                boolean isKeycloakDBReady = keycloakDBDeploymentReadyCondition.isMet(null, cr, context);
                if (!isKeycloakDBReady) {
                    logger.info("Waiting for the Keycloak DB to be ready");
                    return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
                }
            }

            // Keycloak Server
            Keycloak kcInstance = keycloakServerService.getCurrentInstance(cr)
                    .orElseGet(() -> {
                        logger.info("Creating a Keycloak Server");
                        return keycloakServerService.initInstance(cr);
                    });
            boolean isKcInstanceReady = KeycloakUtils.isKeycloakServerReady(kcInstance);
            if (!isKcInstanceReady) {
                logger.info("Waiting for the Keycloak Server to be ready");
                return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
            } else {
                keycloakInstance.set(kcInstance);
            }

            // Keycloak Realm
            KeycloakRealmImport realmImportInstance = keycloakRealmService.getCurrentInstance(cr)
                    .orElseGet(() -> {
                        logger.info("Creating a KeycloakRealmImport");
                        return keycloakRealmService.initInstance(cr);
                    });
            boolean isRealmImportInstanceReady = KeycloakUtils.isKeycloakRealmImportReady(realmImportInstance);
            if (!isRealmImportInstanceReady) {
                logger.info("Waiting for the KeycloakRealmImport to be ready");
                return Optional.of(UpdateControl.<Trustify>noUpdate().rescheduleAfter(5, TimeUnit.SECONDS));
            } else {
                keycloakRealmImportInstance.set(realmImportInstance);
            }
        }

        return Optional.empty();
    }

    private UpdateControl<Trustify> createOrUpdateDependantResources(Trustify cr, Context<Trustify> context) {
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
    public DeleteControl cleanup(Trustify cr, Context<Trustify> context) {
        keycloakRealmService.cleanupDependentResources(cr);
        keycloakServerService.cleanupDependentResources(cr);

        return DeleteControl.defaultDelete();
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
