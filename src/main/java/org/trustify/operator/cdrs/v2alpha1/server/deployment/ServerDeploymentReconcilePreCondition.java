package org.trustify.operator.cdrs.v2alpha1.server.deployment;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeploymentReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;
import org.trustify.operator.services.KeycloakRealmService;
import org.trustify.operator.services.KeycloakServerService;

public class ServerDeploymentReconcilePreCondition implements Condition<Deployment, Trustify> {

    @Override
    public boolean isMet(DependentResource<Deployment, Trustify> dependentResource, Trustify cr, Context<Trustify> context) {
        boolean isDBRequired = ServerUtils.isServerDBRequired(cr);
        if (isDBRequired) {
            DBDeploymentReadyPostCondition dbDeploymentReadyPostCondition = new DBDeploymentReadyPostCondition();
            boolean isDBReady = dbDeploymentReadyPostCondition.isMet(dependentResource, cr, context);
            if (!isDBReady) {
                return false;
            }
        }

        boolean isKcRequired = KeycloakUtils.isKeycloakRequired(cr);
        if (isKcRequired) {
            KeycloakServerService keycloakServerService = context.managedDependentResourceContext().getMandatory(Constants.CONTEXT_KEYCLOAK_SERVER_SERVICE_KEY, KeycloakServerService.class);
            KeycloakRealmService keycloakRealmService = context.managedDependentResourceContext().getMandatory(Constants.CONTEXT_KEYCLOAK_REALM_SERVICE_KEY, KeycloakRealmService.class);

            Boolean isKeycloakReady = keycloakServerService.getCurrentInstance(cr)
                    .map(KeycloakUtils::isKeycloakServerReady)
                    .orElse(false);
            if (!isKeycloakReady) {
                return false;
            }

            Boolean isKeycloakImportReady = keycloakRealmService.getCurrentInstance(cr)
                    .map(KeycloakUtils::isKeycloakRealmImportReady)
                    .orElse(false);
            return isKeycloakImportReady;
        }

        return true;
    }

}
