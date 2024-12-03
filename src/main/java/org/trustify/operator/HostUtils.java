package org.trustify.operator;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.quarkus.logging.Log;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.utils.CRDUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class HostUtils {

    public static boolean isOpenshift(KubernetesClient k8sClient) {
        return k8sClient.supports("route.openshift.io/v1", "Route");
    }

    public static Optional<String> getHostnameForIngress(Trustify cr, KubernetesClient k8sClient) {
        Optional<String> userDefinedHost = CRDUtils.getValueFromSubSpec(cr.getSpec().hostnameSpec(), TrustifySpec.HostnameSpec::hostname);
        if (userDefinedHost.isPresent()) {
            return userDefinedHost;
        }

        boolean isOpenShift = isOpenshift(k8sClient);
        if (isOpenShift) {
            return getClusterDomainOnOpenshift(k8sClient)
                    .map(domain -> cr.getMetadata().getNamespace() + "-" + cr.getMetadata().getName() + "." + domain);
        }

        return Optional.empty();
    }

    public static Optional<String> getHostnameForKeycloak(Trustify cr, KubernetesClient k8sClient) {
        Optional<String> result = getHostnameForIngress(cr, k8sClient);
        if (result.isPresent()) {
            return result;
        }

        return Optional.ofNullable(k8sClient.getMasterUrl().getHost());
    }

    public static Optional<String> getClusterDomainOnOpenshift(KubernetesClient k8sClient) {
        String clusterDomain = null;
        try {
            CustomResourceDefinitionContext customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                    .withName("Ingress")
                    .withGroup("config.openshift.io")
                    .withVersion("v1")
                    .withPlural("ingresses")
                    .withScope("Cluster")
                    .build();
            GenericKubernetesResource clusterObject = k8sClient.genericKubernetesResources(customResourceDefinitionContext)
                    .withName("cluster")
                    .get();

            Map<String, String> objectSpec = Optional.ofNullable(clusterObject)
                    .map(kubernetesResource -> kubernetesResource.<Map<String, String>>get("spec"))
                    .orElse(Collections.emptyMap());
            clusterDomain = objectSpec.get("domain");
        } catch (KubernetesClientException exception) {
            Log.info("No Openshift host found");
        }

        return Optional.ofNullable(clusterDomain);
    }

}
