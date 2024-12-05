package org.trustify.operator.cdrs.v2alpha1.server.db.secret;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

import java.util.Map;
import java.util.Random;

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

    @SuppressWarnings("unchecked")
    private Secret newSecret(Trustify cr, Context<Trustify> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new SecretBuilder()
                .withNewMetadata()
                .withName(getSecretName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .addToLabels(CRDUtils.getLabelsFromString(LABEL_SELECTOR))
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .addToStringData(Constants.DB_SECRET_USERNAME, generateRandomString(10))
                .addToStringData(Constants.DB_SECRET_PASSWORD, generateRandomString(10))
                .build();
    }

    public static String getSecretName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.DB_SECRET_SUFFIX;
    }

    public static String generateRandomString(int targetStringLength) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'

        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
