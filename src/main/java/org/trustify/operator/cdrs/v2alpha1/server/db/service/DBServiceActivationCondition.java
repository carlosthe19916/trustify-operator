package org.trustify.operator.cdrs.v2alpha1.server.db.service;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.db.DBActivationCondition;

@ApplicationScoped
public class DBServiceActivationCondition extends DBActivationCondition implements Condition<Service, Trustify> {

    @Override
    public boolean isMet(DependentResource<Service, Trustify> resource, Trustify cr, Context<Trustify> context) {
        return super.isMet(cr);
    }

}