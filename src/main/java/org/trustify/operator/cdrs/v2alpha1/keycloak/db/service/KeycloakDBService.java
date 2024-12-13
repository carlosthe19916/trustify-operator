package org.trustify.operator.cdrs.v2alpha1.keycloak.db.service;

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
import org.trustify.operator.cdrs.v2alpha1.keycloak.db.deployment.KeycloakDBDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;

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

    private Service newService(Trustify cr, Context<Trustify> context) {
        return new ServiceBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getServiceName(cr), LABEL_SELECTOR, cr))
                        .build()
                )
                .withSpec(getServiceSpec(cr))
                .build();
    }

    private ServiceSpec getServiceSpec(Trustify cr) {
        return new ServiceSpecBuilder()
                .addNewPort()
                .withPort(KeycloakDBDeployment.getDatabasePort(cr))
                .withProtocol(Constants.SERVICE_PROTOCOL)
                .endPort()
                .withSelector(KeycloakDBDeployment.getPodSelectorLabels(cr))
                .withType("ClusterIP")
                .build();
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.OIDC_DB_SERVICE_SUFFIX;
    }

    public static int getServicePort(Trustify cr) {
        return KeycloakDBDeployment.getDatabasePort(cr);
    }

    public static String getServiceHost(Trustify cr) {
        return String.format("%s.%s.svc", getServiceName(cr), cr.getMetadata().getNamespace());
    }

}
