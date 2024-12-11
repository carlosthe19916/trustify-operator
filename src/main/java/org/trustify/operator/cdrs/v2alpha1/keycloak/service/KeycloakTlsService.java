package org.trustify.operator.cdrs.v2alpha1.keycloak.service;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.services.Cluster;

@KubernetesDependent(labelSelector = KeycloakTlsService.LABEL_SELECTOR, resourceDiscriminator = KeycloakTlsServiceDiscriminator.class)
@ApplicationScoped
public class KeycloakTlsService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=keycloak-tls";

    public KeycloakTlsService() {
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
                        .addToAnnotations("service.beta.openshift.io/serving-cert-secret-name", Cluster.getKeycloakSelfGeneratedTlsSecretName(cr))
                        .build()
                )
                .withSpec(getServiceSpec(cr))
                .build();
    }

    private ServiceSpec getServiceSpec(Trustify cr) {
        return new ServiceSpecBuilder()
                .withPorts(
                        new ServicePortBuilder()
                                .withName("http")
                                .withPort(8080)
                                .withProtocol(Constants.SERVICE_PROTOCOL)
                                .build()
                )
                .withClusterIP("None")
                .build();
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + "-" + Constants.TRUSTI_NAME + "-keycloak-tls-service";
    }

}
