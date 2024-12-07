package org.trustify.operator.cdrs.v2alpha1.server.service;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;
import org.trustify.operator.utils.CRDUtils;

@KubernetesDependent(labelSelector = ServerService.LABEL_SELECTOR, resourceDiscriminator = ServerServiceDiscriminator.class)
@ApplicationScoped
public class ServerService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    public ServerService() {
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
                .withPorts(
                        new ServicePortBuilder()
                                .withName("http")
                                .withPort(getServicePort(cr))
                                .withProtocol(Constants.SERVICE_PROTOCOL)
                                .build(),
                        new ServicePortBuilder()
                                .withName("http-infra")
                                .withPort(getServiceInfraestructurePort(cr))
                                .withProtocol(Constants.SERVICE_PROTOCOL)
                                .build()
                )
                .withSelector(ServerDeployment.getPodSelectorLabels(cr))
                .withType("ClusterIP")
                .build();
    }

    public static int getServicePort(Trustify cr) {
        return Constants.HTTP_PORT;
    }

    public static int getServiceInfraestructurePort(Trustify cr) {
        return Constants.HTTP_INFRAESTRUCTURE_PORT;
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.SERVER_SERVICE_SUFFIX;
    }

    public static String getServiceUrl(Trustify cr) {
        return String.format("http://%s:%s", getServiceName(cr), getServicePort(cr));
    }

    public static boolean isTlsConfigured(Trustify cr) {
        var tlsSecret = CRDUtils.getValueFromSubSpec(cr.getSpec().httpSpec(), TrustifySpec.HttpSpec::tlsSecret);
        return tlsSecret.isPresent() && !tlsSecret.get().trim().isEmpty();
    }
}
