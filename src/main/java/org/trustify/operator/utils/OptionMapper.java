package org.trustify.operator.utils;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OptionMapper<T> {
    private final T categorySpec;
    private final List<EnvVar> envVars;

    public OptionMapper(T optionSpec) {
        this.categorySpec = optionSpec;
        this.envVars = new ArrayList<>();
    }

    public List<EnvVar> getEnvVars() {
        return envVars;
    }

    public <R> OptionMapper<T> mapOption(String optionName, Function<T, R> optionValueSupplier) {
        if (categorySpec == null) {
            Log.debugf("No category spec provided for %s", optionName);
            return this;
        }

        R value = optionValueSupplier.apply(categorySpec);

        if (value == null || value.toString().trim().isEmpty()) {
            Log.debugf("No value provided for %s", optionName);
            return this;
        }

        EnvVarBuilder envVarBuilder = new EnvVarBuilder()
                .withName(optionName);

        if (value instanceof SecretKeySelector) {
            envVarBuilder.withValueFrom(new EnvVarSourceBuilder().withSecretKeyRef((SecretKeySelector) value).build());
        } else {
            envVarBuilder.withValue(String.valueOf(value));
        }

        envVars.add(envVarBuilder.build());

        return this;
    }

    public <R> OptionMapper<T> mapOption(String optionName) {
        return mapOption(optionName, s -> null);
    }

    public <R> OptionMapper<T> mapOption(String optionName, R optionValue) {
        return mapOption(optionName, s -> optionValue);
    }

    protected <R extends Collection<?>> OptionMapper<T> mapOptionFromCollection(String optionName, Function<T, R> optionValueSupplier) {
        return mapOption(optionName, s -> {
            var value = optionValueSupplier.apply(s);
            if (value == null) return null;
            return value.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
        });
    }
}
