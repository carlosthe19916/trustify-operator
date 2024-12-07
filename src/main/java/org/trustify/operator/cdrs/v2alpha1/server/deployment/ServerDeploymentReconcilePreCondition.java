package org.trustify.operator.cdrs.v2alpha1.server.deployment;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeploymentReadyPostCondition;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;

@ApplicationScoped
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

        return true;
    }

}
