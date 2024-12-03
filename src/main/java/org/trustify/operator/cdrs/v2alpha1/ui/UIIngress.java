package org.trustify.operator.cdrs.v2alpha1.ui;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.utils.CRDUtils;
import org.trustify.operator.HostUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@KubernetesDependent(labelSelector = UIIngress.LABEL_SELECTOR, resourceDiscriminator = UIIngressDiscriminator.class)
@ApplicationScoped
public class UIIngress extends CRUDKubernetesDependentResource<Ingress, Trustify>
        implements Condition<Ingress, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=ui,component-variant=https";

    public UIIngress() {
        super(Ingress.class);
    }

    @SuppressWarnings("unchecked")
    protected Ingress newIngress(Trustify cr, Context<Trustify> context, String ingressName, Map<String, String> additionalLabels, Map<String, String> additionalAnnotations) {
        KubernetesClient k8sClient = context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_KUBERNETES_CLIENT_KEY, KubernetesClient.class);

        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        var port = UIService.getServicePort(cr);

        String hostname = HostUtils.getHostnameForIngress(cr, k8sClient).orElse(null);
        IngressTLS ingressTLS = getIngressTLS(cr);
        List<IngressTLS> ingressTLSList = ingressTLS != null ? Collections.singletonList(ingressTLS) : Collections.emptyList();

        return new IngressBuilder()
                .withNewMetadata()
                .withName(ingressName)
                .withNamespace(cr.getMetadata().getNamespace())
                .withAnnotations(additionalAnnotations)
                .withLabels(labels)
                .addToLabels(additionalLabels)
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withNewSpec()
                .addNewRule()
                .withHost(hostname)
                .withNewHttp()
                .addNewPath()
                .withPath("/")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName(UIService.getServiceName(cr))
                .withNewPort()
                .withNumber(port)
                .endPort()
                .endService()
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .withTls(ingressTLSList)
                .endSpec()
                .build();
    }

    @Override
    protected Ingress desired(Trustify cr, Context<Trustify> context) {
        return newIngress(
                cr,
                context,
                getIngressName(cr),
                Map.of(
                        "component", "ui",
                        "component-variant", "https"
                ),
                Collections.emptyMap()
        );
    }

    @Override
    public boolean isMet(DependentResource<Ingress, Trustify> dependentResource, Trustify primary, Context<Trustify> context) {
        return context.getSecondaryResource(Ingress.class, new UIIngressDiscriminator())
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

    protected IngressTLS getIngressTLS(Trustify cr) {
        String tlsSecretName = CRDUtils.getValueFromSubSpec(cr.getSpec().httpSpec(), TrustifySpec.HttpSpec::tlsSecret)
                .orElse(null);

        return new IngressTLSBuilder()
                .withSecretName(tlsSecretName)
                .build();
    }

    public static String getIngressName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.INGRESS_SUFFIX;
    }

}
