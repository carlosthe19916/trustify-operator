package org.trustify.operator.cdrs.v2alpha1.ui.service;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.ui.deployment.UIDeployment;

@KubernetesDependent(labelSelector = UIService.LABEL_SELECTOR, resourceDiscriminator = UIServiceDiscriminator.class)
@ApplicationScoped
public class UIService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=ui";

    public UIService() {
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
                                .build()
                )
                .withSelector(UIDeployment.getPodSelectorLabels(cr))
                .withType("ClusterIP")
                .build();
    }

    public static int getServicePort(Trustify cr) {
        return UIDeployment.getDeploymentPort(cr);
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.UI_SERVICE_SUFFIX;
    }

}
