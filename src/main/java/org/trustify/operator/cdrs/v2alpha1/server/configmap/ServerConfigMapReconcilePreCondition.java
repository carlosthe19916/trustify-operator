package org.trustify.operator.cdrs.v2alpha1.server.configmap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.ServerReconcilePreCondition;

public class ServerConfigMapReconcilePreCondition extends ServerReconcilePreCondition implements Condition<ConfigMap, Trustify> {

    @Override
    public boolean isMet(DependentResource<ConfigMap, Trustify> dependentResource, Trustify cr, Context<Trustify> context) {
        return super.isMet(cr, context);
    }

}
