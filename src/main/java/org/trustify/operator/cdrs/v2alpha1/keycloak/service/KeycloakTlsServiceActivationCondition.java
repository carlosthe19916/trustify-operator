package org.trustify.operator.cdrs.v2alpha1.keycloak.service;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.services.ClusterService;
import org.trustify.operator.services.OpenshiftCluster;

public class KeycloakTlsServiceActivationCondition implements Condition<Service, Trustify> {

    @Override
    public boolean isMet(DependentResource<Service, Trustify> resource, Trustify cr, Context<Trustify> context) {
        final var clusterService = context.managedDependentResourceContext().getMandatory(Constants.CLUSTER_SERVICE, ClusterService.class);
        return clusterService.getCluster() instanceof OpenshiftCluster;
    }

}
