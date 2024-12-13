package org.trustify.operator.cdrs.v2alpha1.server.db.secret;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

@KubernetesDependent(labelSelector = DBSecret.LABEL_SELECTOR, resourceDiscriminator = DBSecretDiscriminator.class)
@ApplicationScoped
public class DBSecret extends CRUDKubernetesDependentResource<Secret, Trustify> implements Creator<Secret, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=db";

    public DBSecret() {
        super(Secret.class);
    }

    @Override
    protected Secret desired(Trustify cr, Context<Trustify> context) {
        return newSecret(cr, context);
    }

    @Override
    public Matcher.Result<Secret> match(Secret actual, Trustify cr, Context<Trustify> context) {
        final var desiredSecretName = getSecretName(cr);
        return Matcher.Result.nonComputed(actual.getMetadata().getName().equals(desiredSecretName));
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
        return cr.getMetadata().getName() + Constants.DB_SECRET_SUFFIX;
    }

    public static String getSecretUsernameKey(Trustify cr) {
        return Constants.DB_SECRET_USERNAME;
    }

    public static String getSecretPasswordKey(Trustify cr) {
        return Constants.DB_SECRET_PASSWORD;
    }

    public static SecretKeySelector getUsernameSecretKeySelector(Trustify cr) {
        return new SecretKeySelector(
                getSecretUsernameKey(cr),
                getSecretName(cr),
                false
        );
    }

    public static SecretKeySelector getPasswordSecretKeySelector(Trustify cr) {
        return new SecretKeySelector(
                getSecretPasswordKey(cr),
                getSecretName(cr),
                false
        );
    }

    public static String generateRandomString(int targetStringLength) {
        return CRDUtils.generateRandomString(targetStringLength);
    }
}
