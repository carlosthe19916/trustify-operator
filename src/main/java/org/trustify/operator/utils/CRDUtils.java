package org.trustify.operator.utils;

import io.fabric8.kubernetes.api.model.*;
import org.trustify.operator.TrustifyConfig;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class CRDUtils {

    public static OwnerReference getOwnerReference(Trustify cr) {
        return new OwnerReferenceBuilder()
                .withApiVersion(cr.getApiVersion())
                .withKind(cr.getKind())
                .withName(cr.getMetadata().getName())
                .withUid(cr.getMetadata().getUid())
                .withController(true)
                .build();
    }

    public static <T, R> Optional<R> getValueFromSubSpec(T subSpec, Function<T, R> valueSupplier) {
        if (subSpec != null) {
            return Optional.ofNullable(valueSupplier.apply(subSpec));
        } else {
            return Optional.empty();
        }
    }

    public static Map<String, String> getLabelsFromString(String labels) {
        Map<String, String> result = new HashMap<>();
        Arrays.stream(labels.split(",")).forEach(s -> {
            String[] split = s.split("=");
            if (split.length == 2) {
                result.put(split[0], split[1]);
            }
        });
        return result;
    }

    public static ResourceRequirements getResourceRequirements(TrustifySpec.ResourcesLimitSpec resourcesLimitSpec, TrustifyConfig trustifyConfig) {
        return new ResourceRequirementsBuilder()
                .withRequests(Map.of(
                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuRequest).orElse(trustifyConfig.defaultRequestedCpu())),
                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryRequest).orElse(trustifyConfig.defaultRequestedMemory()))
                ))
                .withLimits(Map.of(
                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::cpuLimit).orElse(trustifyConfig.defaultLimitCpu())),
                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, TrustifySpec.ResourcesLimitSpec::memoryLimit).orElse(trustifyConfig.defaultLimitMemory()))
                ))
                .build();
    }
}
