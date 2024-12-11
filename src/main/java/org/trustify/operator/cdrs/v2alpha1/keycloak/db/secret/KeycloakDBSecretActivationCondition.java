package org.trustify.operator.cdrs.v2alpha1.keycloak.db.secret;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.KeycloakDBActivationCondition;

import java.util.Optional;

public class KeycloakDBSecretActivationCondition extends KeycloakDBActivationCondition implements Condition<Secret, Trustify> {

    @Override
    public boolean isMet(DependentResource<Secret, Trustify> resource, Trustify cr, Context<Trustify> context) {
        boolean databaseRequired = super.isMet(cr);

        boolean manualSecretIsNotSet = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> Optional.ofNullable(oidcSpec.embeddedOidcSpec()))
                .flatMap(embeddedOidcSpec -> Optional.ofNullable(embeddedOidcSpec.databaseSpec()))
                .map(databaseSpec -> databaseSpec.usernameSecret() == null || databaseSpec.passwordSecret() == null)
                .orElse(true);

        return databaseRequired && manualSecretIsNotSet;
    }

}
