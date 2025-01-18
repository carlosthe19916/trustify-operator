package org.trustify.operator.controllers;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public interface ResourceConfigurator {

    record Config(
            String image,
            String imagePullPolicy,
            List<LocalObjectReference> imagePullSecrets,
            ResourceRequirements resourceRequirements,
            List<EnvVar> allEnvVars,
            List<Volume> allVolumes,
            List<VolumeMount> allVolumeMounts
    ) {

        public boolean match(PodSpec podSpec) {
            boolean imagePullSecretsMatch = Objects.equals(new HashSet<>(imagePullSecrets()), new HashSet<>(podSpec.getImagePullSecrets()));
            if (!imagePullSecretsMatch) {
                return false;
            }

            boolean volumesMatch = Objects.equals(
                    allVolumes().stream().map(Volume::getName).collect(Collectors.toSet()),
                    podSpec.getVolumes().stream().map(Volume::getName).collect(Collectors.toSet())
            );
            if (!volumesMatch) {
                return false;
            }

            return podSpec
                    .getContainers()
                    .stream().allMatch(container -> {
                        boolean isImageCorrect = Objects.equals(image(), container.getImage());
                        if (!isImageCorrect) {
                            return false;
                        }

                        boolean isImagePullPolicyCorrect = Objects.equals(imagePullPolicy(), container.getImagePullPolicy());
                        if (!isImagePullPolicyCorrect) {
                            return false;
                        }

                        boolean resourcesMatch = Objects.equals(resourceRequirements(), container.getResources());
                        if (!resourcesMatch) {
                            return false;
                        }

                        boolean envVarsMatch = new HashSet<>(container.getEnv()).containsAll(allEnvVars());
                        if (!envVarsMatch) {
                            return false;
                        }

                        boolean volumeMountsMatch = new HashSet<>(container.getVolumeMounts()).containsAll(allVolumeMounts());
                        if (!volumeMountsMatch) {
                            return false;
                        }

                        return true;
                    });
        }
    }

    Config configureDeployment(Trustify cr, Context<Trustify> context);

}
