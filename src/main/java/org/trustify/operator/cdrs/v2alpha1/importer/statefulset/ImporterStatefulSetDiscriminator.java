package org.trustify.operator.cdrs.v2alpha1.importer.statefulset;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.controllers.TrustifyReconciler;

import java.util.Optional;

public class ImporterStatefulSetDiscriminator implements ResourceDiscriminator<StatefulSet, Trustify> {
    @Override
    public Optional<StatefulSet> distinguish(Class<StatefulSet> resource, Trustify cr, Context<Trustify> context) {
        String statefulSetName = ImporterStatefulSet.getStatefulSetName(cr);
        ResourceID resourceID = new ResourceID(statefulSetName, cr.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<StatefulSet, Trustify>) context.eventSourceRetriever().getResourceEventSourceFor(StatefulSet.class, TrustifyReconciler.STATEFUL_SET_EVENT_SOURCE);
        return informerEventSource.get(resourceID);
    }
}