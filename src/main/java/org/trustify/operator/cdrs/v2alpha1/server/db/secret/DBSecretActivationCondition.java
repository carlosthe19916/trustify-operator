package org.trustify.operator.cdrs.v2alpha1.server.db.secret;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.db.DBActivationCondition;

import java.util.Optional;

public class DBSecretActivationCondition extends DBActivationCondition implements Condition<Secret, Trustify> {

    @Override
    public boolean isMet(DependentResource<Secret, Trustify> resource, Trustify cr, Context<Trustify> context) {
        boolean databaseRequired = super.isMet(cr);

        boolean manualSecretIsNotSet = Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(databaseSpec -> databaseSpec.usernameSecret() == null || databaseSpec.passwordSecret() == null)
                .orElse(true);

        return databaseRequired && manualSecretIsNotSet;
    }

}
