package org.trustify.operator.cdrs.v2alpha1.keycloak.utils;

import org.keycloak.k8s.v2alpha1.Keycloak;
import org.keycloak.k8s.v2alpha1.KeycloakRealmImport;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;

import java.util.Objects;
import java.util.Optional;

public class KeycloakUtils {

    public static boolean isKeycloakRequired(Trustify cr) {
        return Optional.ofNullable(cr.getSpec().oidcSpec())
                .map(oidcSpec -> oidcSpec.enabled() && (oidcSpec.type() == null || Objects.equals(oidcSpec.type(), TrustifySpec.OidcProviderType.EMBEDDED)))
                .orElse(false);
    }

    public static boolean isKeycloakDBRequired(Trustify cr) {
        boolean keycloakRequired = KeycloakUtils.isKeycloakRequired(cr);
        if (!keycloakRequired) {
            return false;
        }

        return !Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> Optional.ofNullable(oidcSpec.embeddedOidcSpec()))
                .flatMap(embeddedOidcSpec -> Optional.ofNullable(embeddedOidcSpec.databaseSpec()))
                .map(TrustifySpec.DatabaseSpec::externalDatabase)
                .orElse(false);
    }

    public static boolean isKeycloakServerReady(Keycloak kcInstance) {
        return kcInstance.getStatus() != null && kcInstance.getStatus()
                .getConditions().stream()
                .anyMatch(condition -> Objects.equals(condition.getType(), "Ready") && Objects.equals(condition.getStatus(), "True"));
    }

    public static boolean isKeycloakRealmImportReady(KeycloakRealmImport realmImportInstance) {
        return realmImportInstance.getStatus() != null && realmImportInstance.getStatus()
                .getConditions().stream()
                .anyMatch(condition -> Objects.equals(condition.getType(), "Done") && Objects.equals(condition.getStatus(), "True"));
    }

}
