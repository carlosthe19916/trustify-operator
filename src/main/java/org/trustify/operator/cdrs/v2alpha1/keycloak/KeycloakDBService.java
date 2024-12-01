package org.trustify.operator.cdrs.v2alpha1.keycloak;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

import java.util.Map;

@KubernetesDependent(labelSelector = KeycloakDBService.LABEL_SELECTOR, resourceDiscriminator = KeycloakDBServiceDiscriminator.class)
@ApplicationScoped
public class KeycloakDBService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=keycloak";

    public KeycloakDBService() {
        super(Service.class);
    }

    @Override
    public Service desired(Trustify cr, Context<Trustify> context) {
        return newService(cr, context);
    }

    @SuppressWarnings("unchecked")
    private Service newService(Trustify cr, Context<Trustify> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new ServiceBuilder()
                .withNewMetadata()
                .withName(getServiceName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .addToLabels("component", "keycloak")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getServiceSpec(cr))
                .build();
    }

    private ServiceSpec getServiceSpec(Trustify cr) {
        return new ServiceSpecBuilder()
                .addNewPort()
                .withPort(KeycloakDBDeployment.getDatabasePort(cr))
                .withProtocol(Constants.SERVICE_PROTOCOL)
                .endPort()
                .withSelector(Constants.OIDC_DB_SELECTOR_LABELS)
                .withType("ClusterIP")
                .build();
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.OIDC_DB_SERVICE_SUFFIX;
    }

}