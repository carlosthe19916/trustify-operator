package org.trustify.operator.cdrs.v2alpha1.importer.statefulset;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeploymentReadyPostCondition;

public class ImporterStatefulSetReconcilePreCondition implements Condition<Deployment, Trustify> {

    @Override
    public boolean isMet(DependentResource<Deployment, Trustify> dependentResource, Trustify cr, Context<Trustify> context) {
        ServerDeploymentReadyPostCondition serverDeploymentReadyPostCondition = new ServerDeploymentReadyPostCondition();
        return serverDeploymentReadyPostCondition.isMet(dependentResource, cr, context);
    }

}
