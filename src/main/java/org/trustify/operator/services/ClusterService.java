package org.trustify.operator.services;

import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClusterService {

    @Inject
    KubernetesClient k8sClient;

    private Cluster cluster;

    @PostConstruct
    void init() {
        boolean isOpenshift = k8sClient.supports("route.openshift.io", "routes");
        if (isOpenshift) {
            cluster = new OpenshiftCluster(k8sClient);
        } else {
            cluster = new VanillaCluster(k8sClient);
        }
    }

    public Cluster getCluster() {
        return cluster;
    }
}
