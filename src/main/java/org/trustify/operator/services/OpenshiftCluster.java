package org.trustify.operator.services;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class OpenshiftCluster implements Cluster {

    private static final Logger logger = Logger.getLogger(OpenshiftCluster.class);

    private final KubernetesClient k8sClient;

    private String hostname;

    public OpenshiftCluster(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
        init();
    }

    @Override
    public Optional<String> getClusterHostname() {
        return Optional.ofNullable(hostname);
    }

    protected void init() {
        this.hostname = getClusterDomainOnOpenshift();
    }

    protected String getClusterDomainOnOpenshift() {
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
            logger.warn("No Openshift host found");
        }
        return clusterDomain;
    }

}
