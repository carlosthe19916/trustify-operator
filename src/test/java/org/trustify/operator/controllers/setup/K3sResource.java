package org.trustify.operator.controllers.setup;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class K3sResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger logger = Logger.getLogger(K3sConfigProducer.class);

    static K3sContainer k3sContainer;

    // If ENV HOST_KUBERNETES_CONFIG_FILE is set then use the host k8s config
    public static final String HOST_KUBERNETES_CONFIG_FILE = "HOST_KUBERNETES_CONFIG_FILE";

    // If ENV HOST_KUBERNETES_CONFIG_FILE is not set then rancher/k3s for k8s. If KUBERNETES_VERSION is not set then "latest" is used
    public static final String KUBERNETES_VERSION = "KUBERNETES_VERSION";

    @Override
    public Map<String, String> start() {
        Map<String, String> result = new HashMap<>();
        result.put("quarkus.kubernetes.namespace", "trustify-operator1");

        String kubeConfigYaml;
        Optional<String> hostKubernetesConfigFile = Optional.ofNullable(System.getenv(HOST_KUBERNETES_CONFIG_FILE));
        if (hostKubernetesConfigFile.isPresent()) {
            logger.info("Using " + hostKubernetesConfigFile.get() + " as kubernetes config file");
            try {
                kubeConfigYaml = Files.readString(Paths.get(hostKubernetesConfigFile.get()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            String rancherVersion = Optional.ofNullable(System.getenv(KUBERNETES_VERSION)).orElse("latest");
            logger.info("Using rancher/k3s:" + rancherVersion);

            k3sContainer = new K3sContainer(DockerImageName.parse("rancher/k3s:" + rancherVersion))
                    .withCommand("server", "--tls-san=${DockerClientFactory.instance().dockerHostIpAddress()}")
                    .withExposedPorts(443, 6443);
            k3sContainer.start();

            result.put("k3sClusterHost", "localhost:" + k3sContainer.getMappedPort(443));
            kubeConfigYaml = k3sContainer.getKubeConfigYaml();
        }

        result.put("kubeConfigYaml", kubeConfigYaml);
        return result;
    }

    @Override
    public void stop() {
        if (k3sContainer != null) {
            k3sContainer.stop();
        }
    }
}
