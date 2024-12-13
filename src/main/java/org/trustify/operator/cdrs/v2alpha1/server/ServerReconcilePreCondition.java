package org.trustify.operator.cdrs.v2alpha1.server;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;
import org.trustify.operator.services.KeycloakRealmService;
import org.trustify.operator.services.KeycloakServerService;

import java.util.Optional;

public abstract class ServerReconcilePreCondition {

    public boolean isMet(Trustify cr, Context<Trustify> context) {
        boolean isKcRequired = KeycloakUtils.isKeycloakRequired(cr);
        if (isKcRequired) {
            Optional<KeycloakServerService> keycloakServerService = context.managedDependentResourceContext().get(Constants.CONTEXT_KEYCLOAK_SERVER_SERVICE_KEY, KeycloakServerService.class);
            Optional<KeycloakRealmService> keycloakRealmService = context.managedDependentResourceContext().get(Constants.CONTEXT_KEYCLOAK_REALM_SERVICE_KEY, KeycloakRealmService.class);

            if (keycloakServerService.isEmpty() || keycloakRealmService.isEmpty()) {
                return false;
            }

            Boolean isKeycloakReady = keycloakServerService.get().getCurrentInstance(cr)
                    .map(KeycloakUtils::isKeycloakServerReady)
                    .orElse(false);
            if (!isKeycloakReady) {
                return false;
            }

            Boolean isKeycloakImportReady = keycloakRealmService.get().getCurrentInstance(cr)
                    .map(KeycloakUtils::isKeycloakRealmImportReady)
                    .orElse(false);
            return isKeycloakImportReady;
        }

        return true;
    }

}
