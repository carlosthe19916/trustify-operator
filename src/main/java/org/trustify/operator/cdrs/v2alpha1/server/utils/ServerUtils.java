package org.trustify.operator.cdrs.v2alpha1.server.utils;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.utils.CRDUtils;

import java.util.Optional;

@ApplicationScoped
public class ServerUtils {

    @Inject
    KubernetesClient k8sClient;

    public static boolean isServerDBRequired(Trustify cr) {
        return !Optional.ofNullable(cr.getSpec().databaseSpec())
                .map(TrustifySpec.DatabaseSpec::externalDatabase)
                .orElse(false);
    }

    public static String getSelfGeneratedTlsSecretName(Trustify cr) {
        return cr.getMetadata().getName() + "-" + Constants.TRUSTI_SERVER_NAME + "-tls";
    }

    public Optional<String> tlsSecretName(Trustify cr) {
        Optional<String> userDefinedTlsSecretName = CRDUtils.getValueFromSubSpec(cr.getSpec().httpSpec(), TrustifySpec.HttpSpec::tlsSecret);
        if (userDefinedTlsSecretName.isPresent()) {
            return userDefinedTlsSecretName;
        }

        Secret selfGeneratedSecret = new SecretBuilder()
                .withNewMetadata()
                .withName(getSelfGeneratedTlsSecretName(cr))
                .endMetadata()
                .build();
        Secret secret = k8sClient.resource(selfGeneratedSecret)
                .inNamespace(cr.getMetadata().getNamespace())
                .get();
        if (secret != null) {
            return Optional.of(secret.getMetadata().getName());
        }

        return Optional.empty();
    }
}
