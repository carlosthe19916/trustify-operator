package org.trustify.operator.cdrs.v2alpha1.server.configmap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.controllers.TrustifyReconciler;

import java.util.Optional;

public class ServerConfigMapDiscriminator implements ResourceDiscriminator<ConfigMap, Trustify> {
    @Override
    public Optional<ConfigMap> distinguish(Class<ConfigMap> resource, Trustify cr, Context<Trustify> context) {
        String configMap = ServerConfigMap.getConfigMapName(cr);
        ResourceID resourceID = new ResourceID(configMap, cr.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<ConfigMap, Trustify>) context.eventSourceRetriever().getResourceEventSourceFor(ConfigMap.class, TrustifyReconciler.CONFIG_MAP_EVENT_SOURCE);
        return informerEventSource.get(resourceID);
    }
}
