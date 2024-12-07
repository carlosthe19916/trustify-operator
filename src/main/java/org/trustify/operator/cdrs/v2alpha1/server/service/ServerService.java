package org.trustify.operator.cdrs.v2alpha1.server.service;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.server.deployment.ServerDeployment;
import org.trustify.operator.cdrs.v2alpha1.server.utils.ServerUtils;
import org.trustify.operator.services.Cluster;

@KubernetesDependent(labelSelector = ServerService.LABEL_SELECTOR, resourceDiscriminator = ServerServiceDiscriminator.class)
@ApplicationScoped
public class ServerService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    @Inject
    ServerUtils serverUtils;

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
                        .addToAnnotations("service.beta.openshift.io/serving-cert-secret-name", Cluster.getServerSelfGeneratedTlsSecretName(cr))
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
                                .withPort(getServiceInfrastructurePort(cr))
                                .withProtocol(Constants.SERVICE_PROTOCOL)
                                .build()
                )
                .withSelector(ServerDeployment.getPodSelectorLabels(cr))
                .withType("ClusterIP")
                .build();
    }

    public static int getServicePort(Trustify cr) {
        return ServerDeployment.getDeploymentPort(cr);
    }

    public static int getServiceInfrastructurePort(Trustify cr) {
        return ServerDeployment.getDeploymentInfrastructurePort(cr);
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.SERVER_SERVICE_SUFFIX;
    }

    public String getServiceHost(Trustify cr) {
        return String.format("%s.%s.svc", getServiceName(cr), cr.getMetadata().getNamespace());
    }

    public String getServiceUrl(Trustify cr) {
        String protocol = serverUtils.tlsSecretName(cr).isPresent() ? "https" : "http";
        return String.format("%s://%s:%s", protocol, getServiceHost(cr), getServicePort(cr));
    }

}
