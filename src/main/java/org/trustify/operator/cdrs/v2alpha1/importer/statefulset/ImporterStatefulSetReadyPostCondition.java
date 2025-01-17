package org.trustify.operator.cdrs.v2alpha1.importer.statefulset;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

public class ImporterStatefulSetReadyPostCondition implements Condition<StatefulSet, Trustify> {

    @Override
    public boolean isMet(DependentResource<StatefulSet, Trustify> dependentResource, Trustify cr, Context<Trustify> context) {
        return context.getSecondaryResource(StatefulSet.class, new ImporterStatefulSetDiscriminator())
                .map(deployment -> {
                    final var status = deployment.getStatus();
                    if (status != null) {
                        final var readyReplicas = status.getReadyReplicas();
                        return readyReplicas != null && readyReplicas >= 1;
                    }
                    return false;
                })
                .orElse(false);
    }

}
