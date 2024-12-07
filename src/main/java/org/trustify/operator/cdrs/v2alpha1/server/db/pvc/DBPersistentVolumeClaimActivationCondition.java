package org.trustify.operator.cdrs.v2alpha1.server.db.pvc;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.db.DBActivationCondition;

public class DBPersistentVolumeClaimActivationCondition extends DBActivationCondition implements Condition<PersistentVolumeClaim, Trustify> {

    @Override
    public boolean isMet(DependentResource<PersistentVolumeClaim, Trustify> resource, Trustify cr, Context<Trustify> context) {
        return super.isMet(cr);
    }

}
