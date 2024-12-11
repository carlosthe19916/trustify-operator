package org.trustify.operator.cdrs.v2alpha1.keycloak.db.secret;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.controllers.TrustifyReconciler;

import java.util.Optional;

public class KeycloakDBSecretDiscriminator implements ResourceDiscriminator<Secret, Trustify> {
    @Override
    public Optional<Secret> distinguish(Class<Secret> resource, Trustify cr, Context<Trustify> context) {
        String secret = KeycloakDBSecret.getSecretName(cr);
        ResourceID resourceID = new ResourceID(secret, cr.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<Secret, Trustify>) context.eventSourceRetriever().getResourceEventSourceFor(Secret.class, TrustifyReconciler.SECRET_EVENT_SOURCE);
        return informerEventSource.get(resourceID);
    }
}
