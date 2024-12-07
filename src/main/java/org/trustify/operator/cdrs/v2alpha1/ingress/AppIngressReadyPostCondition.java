package org.trustify.operator.cdrs.v2alpha1.ingress;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

public class AppIngressReadyPostCondition implements Condition<Ingress, Trustify> {

    @Override
    public boolean isMet(DependentResource<Ingress, Trustify> dependentResource, Trustify primary, Context<Trustify> context) {
        return context.getSecondaryResource(Ingress.class, new AppIngressDiscriminator())
                .map(in -> {
                    final var status = in.getStatus();
                    if (status != null) {
                        final var ingresses = status.getLoadBalancer().getIngress();
                        // only set the status if the ingress is ready to provide the info we need
                        return ingresses != null && !ingresses.isEmpty();
                    }
                    return false;
                })
                .orElse(false);
    }

}
