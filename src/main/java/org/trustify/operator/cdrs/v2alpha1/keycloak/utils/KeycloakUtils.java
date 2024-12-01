package org.trustify.operator.cdrs.v2alpha1.keycloak.utils;

import okio.ByteString;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.deployment.Keycloak;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImport;
import org.trustify.operator.cdrs.v2alpha1.keycloak.crds.v2alpha1.realmimport.KeycloakRealmImportStatusCondition;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakRealmService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakServerService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;

public class KeycloakUtils {

    public static boolean isKeycloakRequired(Trustify cr) {
        return Optional.ofNullable(cr.getSpec().oidcSpec())
                .map(oidcSpec -> oidcSpec.enabled() && (oidcSpec.type() == null || Objects.equals(oidcSpec.type(), TrustifySpec.OidcProviderType.EMBEDDED)))
                .orElse(false);
    }

    public static String serverUrlWithRealmIncluded(Trustify cr) {
        return KeycloakServerService.getServiceHostUrl(cr) + KeycloakRealmService.getRealmClientPath(cr);
    }

    public static String serverUrlWithRealmIncluded(String url, Trustify cr) {
        return url + KeycloakRealmService.getRealmClientPath(cr);
    }

    public static boolean isKeycloakServerReady(Keycloak kcInstance) {
        return kcInstance.getStatus() != null && kcInstance.getStatus()
                .getConditions().stream()
                .anyMatch(condition -> Objects.equals(condition.getType(), "Ready") && Objects.equals(condition.getStatus(), true));
    }

    public static boolean isKeycloakRealmImportReady(KeycloakRealmImport realmImportInstance) {
        return realmImportInstance.getStatus() != null && realmImportInstance.getStatus()
                .getConditions().stream()
                .anyMatch(condition -> Objects.equals(condition.getType(), KeycloakRealmImportStatusCondition.DONE) && Objects.equals(condition.getStatus(), true));
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048, new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        } catch (GeneralSecurityException var2) {
            throw new AssertionError(var2);
        }
    }

    public static String getCertificatePem(X509CertificateHolder certHolder) throws IOException {
        StringBuilder result = new StringBuilder();
        result.append("-----BEGIN CERTIFICATE-----\n");
        encodeBase64Lines(result, ByteString.of(certHolder.getEncoded()));
        result.append("-----END CERTIFICATE-----\n");
        return result.toString();
    }

    public static String getPrivateKeyPkcs1Pem(KeyPair keyPair) throws IOException {
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(keyPair.getPrivate().getEncoded());
        StringBuilder result = new StringBuilder();
        result.append("-----BEGIN RSA PRIVATE KEY-----\n");
        encodeBase64Lines(result, ByteString.of(privateKeyInfo.parsePrivateKey().toASN1Primitive().getEncoded()));
        result.append("-----END RSA PRIVATE KEY-----\n");
        return result.toString();
    }

    private static void encodeBase64Lines(StringBuilder out, ByteString data) {
        String base64 = data.base64();
        for (int i = 0; i < base64.length(); i += 64) {
            out.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
    }
}