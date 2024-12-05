package org.trustify.operator.services;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.jboss.logging.Logger;

import java.util.Optional;

public class VanillaCluster implements Cluster {

    private static final Logger logger = Logger.getLogger(VanillaCluster.class);

    private final KubernetesClient k8sClient;

    public VanillaCluster(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
    }

    @Override
    public Optional<String> getClusterHostname() {
        return Optional.empty();
    }

}
