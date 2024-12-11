package org.trustify.operator.cdrs.v2alpha1.keycloak.db;

import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;

public abstract class KeycloakDBActivationCondition {

    protected boolean isMet(Trustify cr) {
        return KeycloakUtils.isKeycloakDBRequired(cr);
    }

}
