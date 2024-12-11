package org.trustify.operator.cdrs.v2alpha1.keycloak.db.service;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.controllers.TrustifyReconciler;

import java.util.Optional;

public class KeycloakDBServiceDiscriminator implements ResourceDiscriminator<Service, Trustify> {
    @Override
    public Optional<Service> distinguish(Class<Service> resource, Trustify cr, Context<Trustify> context) {
        String serviceName = KeycloakDBService.getServiceName(cr);
        ResourceID resourceID = new ResourceID(serviceName, cr.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<Service, Trustify>) context.eventSourceRetriever().getResourceEventSourceFor(Service.class, TrustifyReconciler.SERVICE_EVENT_SOURCE);
        return informerEventSource.get(resourceID);
    }
}
