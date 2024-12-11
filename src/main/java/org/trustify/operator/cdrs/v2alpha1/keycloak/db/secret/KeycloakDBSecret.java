package org.trustify.operator.cdrs.v2alpha1.keycloak.db.secret;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

@KubernetesDependent(labelSelector = KeycloakDBSecret.LABEL_SELECTOR, resourceDiscriminator = KeycloakDBSecretDiscriminator.class)
@ApplicationScoped
public class KeycloakDBSecret extends CRUDKubernetesDependentResource<Secret, Trustify> implements Creator<Secret, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=keycloak";

    public KeycloakDBSecret() {
        super(Secret.class);
    }

    @Override
    protected Secret desired(Trustify cr, Context<Trustify> context) {
        return newSecret(cr, context);
    }

    @Override
    public Result<Secret> match(Secret actual, Trustify cr, Context<Trustify> context) {
        final var desiredSecretName = getSecretName(cr);
        return Result.nonComputed(actual.getMetadata().getName().equals(desiredSecretName));
    }

    private Secret newSecret(Trustify cr, Context<Trustify> context) {
        return new SecretBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getSecretName(cr), LABEL_SELECTOR, cr))
                        .build()
                )
                .addToStringData(getSecretUsernameKey(cr), generateRandomString(10))
                .addToStringData(getSecretPasswordKey(cr), generateRandomString(10))
                .build();
    }

    public static String getSecretName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.OIDC_DB_SECRET_SUFFIX;
    }

    public static String getSecretUsernameKey(Trustify cr) {
        return Constants.DB_SECRET_USERNAME;
    }

    public static String getSecretPasswordKey(Trustify cr) {
        return Constants.DB_SECRET_PASSWORD;
    }

    public static SecretKeySelector getUsernameKeySelector(Trustify cr) {
        return new SecretKeySelector(
                KeycloakDBSecret.getSecretUsernameKey(cr),
                KeycloakDBSecret.getSecretName(cr),
                false
        );
    }

    public static SecretKeySelector getPasswordKeySelector(Trustify cr) {
        return new SecretKeySelector(
                KeycloakDBSecret.getSecretPasswordKey(cr),
                KeycloakDBSecret.getSecretName(cr),
                false
        );
    }

    public static String generateRandomString(int targetStringLength) {
        return CRDUtils.generateRandomString(targetStringLength);
    }
}
