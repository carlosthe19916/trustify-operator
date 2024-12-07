package org.trustify.operator.cdrs.v2alpha1.server.pvc;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Objects;
import java.util.Optional;

public class ServerStoragePersistentVolumeClaimActivationCondition implements Condition<PersistentVolumeClaim, Trustify> {

    @Override
    public boolean isMet(DependentResource<PersistentVolumeClaim, Trustify> resource, Trustify cr, Context<Trustify> context) {
        return Optional.ofNullable(cr.getSpec().storageSpec())
                .map(storageSpec -> Objects.isNull(storageSpec.type()) || Objects.equals(TrustifySpec.StorageStrategyType.FILESYSTEM, storageSpec.type()))
                .orElse(true);
    }

}
