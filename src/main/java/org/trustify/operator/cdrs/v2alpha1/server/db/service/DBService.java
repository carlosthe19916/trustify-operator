package org.trustify.operator.cdrs.v2alpha1.server.db.service;

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
import org.trustify.operator.cdrs.v2alpha1.server.db.deployment.DBDeployment;

@KubernetesDependent(labelSelector = DBService.LABEL_SELECTOR, resourceDiscriminator = DBServiceDiscriminator.class)
@ApplicationScoped
public class DBService extends CRUDKubernetesDependentResource<Service, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=db";

    public DBService() {
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
                .withPort(DBDeployment.getDatabasePort(cr))
                .withProtocol(Constants.SERVICE_PROTOCOL)
                .endPort()
                .withSelector(DBDeployment.getPodSelectorLabels(cr))
                .withType("ClusterIP")
                .build();
    }

    public static String getServiceName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.DB_SERVICE_SUFFIX;
    }

}
