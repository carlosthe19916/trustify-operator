package org.trustify.operator.cdrs.v2alpha1.keycloak;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jboss.logging.Logger;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;
import org.trustify.operator.utils.CRDUtils;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.Security;
import java.util.Date;
import java.util.Map;

@KubernetesDependent(labelSelector = KeycloakHttpTlsSecret.LABEL_SELECTOR, resourceDiscriminator = KeycloakHttpTlsSecretDiscriminator.class)
@ApplicationScoped
public class KeycloakHttpTlsSecret extends CRUDKubernetesDependentResource<Secret, Trustify> implements Creator<Secret, Trustify> {

    private static final Logger logger = Logger.getLogger(KeycloakHttpTlsSecret.class);

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=keycloak,feature=tls";

    public KeycloakHttpTlsSecret() {
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

    @SuppressWarnings("unchecked")
    private Secret newSecret(Trustify cr, Context<Trustify> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        // SSL
        String host = "test";

        Security.addProvider(new BouncyCastleProvider());

        X500Principal subject = new X500Principal("CN=" + host);
        KeyPair keyPair = KeycloakUtils.generateKeyPair();

        long notBefore = System.currentTimeMillis();
        long notAfter = notBefore + (1000L * 3600L * 24 * 365);

        ASN1Encodable[] encodableAltNames = new ASN1Encodable[]{new GeneralName(GeneralName.dNSName, host)};
        KeyPurposeId[] purposes = new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth};

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.ONE,
                new Date(notBefore),
                new Date(notAfter),
                subject,
                keyPair.getPublic()
        );

        try {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature + KeyUsage.keyEncipherment));
            certBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purposes));
            certBuilder.addExtension(Extension.subjectAlternativeName, false, new DERSequence(encodableAltNames));

            final ContentSigner signer = new JcaContentSignerBuilder(("SHA256withRSA")).build(keyPair.getPrivate());
            X509CertificateHolder certHolder = certBuilder.build(signer);

            return new SecretBuilder()
                    .withNewMetadata()
                    .withName(getSecretName(cr))
                    .withNamespace(cr.getMetadata().getNamespace())
                    .withLabels(labels)
                    .addToLabels("component", "keycloak")
                    .addToLabels("feature", "tls")
                    .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                    .endMetadata()
                    .withType("kubernetes.io/tls")
                    .addToStringData(getSecretKeyFieldName(), KeycloakUtils.getPrivateKeyPkcs1Pem(keyPair))
                    .addToStringData(getSecretCrtFieldName(), KeycloakUtils.getCertificatePem(certHolder))
                    .build();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new AssertionError(e.getMessage());
        }
    }

    public static String getSecretName(Trustify cr) {
        return cr.getMetadata().getName() + "-" + Constants.KEYCLOAK_NAME + "-tls";
    }

    public static String getSecretKeyFieldName() {
        return "tls.key";
    }

    public static String getSecretCrtFieldName() {
        return "tls.crt";
    }
}
