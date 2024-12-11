package org.trustify.operator.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.keycloak.k8s.v2alpha1.KeycloakRealmImport;
import org.keycloak.k8s.v2alpha1.KeycloakRealmImportSpec;
import org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.Realm;
import org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.*;
import org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.users.Credentials;
import org.trustify.operator.cdrs.v2alpha1.Trustify;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class KeycloakRealmService {

    @Inject
    KubernetesClient k8sClient;

    @Inject
    ObjectMapper objectMapper;

    Function<String, ClientScopes> generateClientScope = scope -> {
        ClientScopes scopeRepresentation = new ClientScopes();
        scopeRepresentation.setName(scope);
        scopeRepresentation.setProtocol("openid-connect");
        return scopeRepresentation;
    };

    public static String getKeycloakRealmImportName(Trustify cr) {
        return cr.getMetadata().getName() + "-realm-import";
    }

    public static String getRealmName(Trustify cr) {
        return "trustify";
    }

    public static String getUIClientName(Trustify cr) {
        return "frontend";
    }

    public static String getRealmClientRelativePath(Trustify cr) {
        return String.format("%s/realms/%s", KeycloakServerService.RELATIVE_PATH, KeycloakRealmService.getRealmName(cr));
    }

    public Optional<KeycloakRealmImport> getCurrentInstance(Trustify cr) {
        KeycloakRealmImport realmImport = k8sClient.resources(KeycloakRealmImport.class)
                .inNamespace(cr.getMetadata().getNamespace())
                .withName(getKeycloakRealmImportName(cr))
                .get();
        return Optional.ofNullable(realmImport);
    }

    public KeycloakRealmImport initInstance(Trustify cr) {
        KeycloakRealmImport realmImport = new KeycloakRealmImport();

        realmImport.setMetadata(new ObjectMeta());
        realmImport.getMetadata().setName(getKeycloakRealmImportName(cr));
        realmImport.setSpec(new KeycloakRealmImportSpec());

        KeycloakRealmImportSpec spec = realmImport.getSpec();
        spec.setKeycloakCRName(KeycloakServerService.getKeycloakName(cr));

        // Realm
        spec.setRealm(getDefaultRealm());
        Realm realmRepresentation = spec.getRealm();
        realmRepresentation.setRealm(getRealmName(cr));

        // Realm roles
        if (realmRepresentation.getRoles() == null) {
            realmRepresentation.setRoles(new Roles());
        }
        if (realmRepresentation.getRoles().getRealm() == null) {
            realmRepresentation.getRoles().setRealm(new ArrayList<>());
        }

        org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.roles.Realm userRole = new org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.roles.Realm();
        userRole.setName("user");
        userRole.setDescription("The user of the application");

        org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.roles.Realm adminRole = new org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.roles.Realm();
        adminRole.setName("admin");
        adminRole.setDescription("Admin of the application");

        realmRepresentation.getRoles().setRealm(List.of(
                userRole,
                adminRole
        ));

        // Scopes
        if (realmRepresentation.getClientScopes() == null) {
            realmRepresentation.setClientScopes(new ArrayList<>());
        }

        ClientScopes readDocumentScope = generateClientScope.apply("read:document");
        ClientScopes createDocumentScope = generateClientScope.apply("create:document");
        ClientScopes updateDocumentScope = generateClientScope.apply("update:document");
        ClientScopes deleteDocumentScope = generateClientScope.apply("delete:document");

        realmRepresentation.getClientScopes().addAll(List.of(
                readDocumentScope,
                createDocumentScope,
                updateDocumentScope,
                deleteDocumentScope
        ));

        // Role-Scope Mapping
        BiConsumer<ClientScopes, List<org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.roles.Realm>> applyRolesToScope = (scopeRepresentation, roles) -> {
            ScopeMappings mapping = new ScopeMappings();

            mapping.setClientScope(scopeRepresentation.getName());
            if (realmRepresentation.getScopeMappings() == null) {
                realmRepresentation.setScopeMappings(new ArrayList<>());
            }
            ;
            realmRepresentation.getScopeMappings().add(mapping);

            mapping.setRoles(roles.stream()
                    .map(org.keycloak.k8s.v2alpha1.keycloakrealmimportspec.realm.roles.Realm::getName)
                    .collect(Collectors.toList())
            );
        };

        applyRolesToScope.accept(readDocumentScope, List.of(adminRole, userRole));
        applyRolesToScope.accept(createDocumentScope, List.of(adminRole));
        applyRolesToScope.accept(updateDocumentScope, List.of(adminRole));
        applyRolesToScope.accept(deleteDocumentScope, List.of(adminRole));

        // Users
        Users adminUser = new Users();
        realmRepresentation.setUsers(List.of(
                adminUser
        ));

        // Admin User
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@trustify.org");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("Admin");
        adminUser.setEnabled(true);
        adminUser.setRealmRoles(List.of(
                "default-roles-trustify",
                "offline_access",
                "uma_authorization",
                adminRole.getName()
        ));

        Credentials adminCredentials = new Credentials();
        adminCredentials.setType("password");
        adminCredentials.setValue("admin");
        adminCredentials.setTemporary(false);

        adminUser.setCredentials(List.of(adminCredentials));

        // Clients
        if (realmRepresentation.getClients() == null || realmRepresentation.getClients().isEmpty()) {
            realmRepresentation.setClients(new ArrayList<>());
        }

        Clients uiClient = new Clients();

        realmRepresentation.getClients().add(
                uiClient
        );

        // UI Client
        uiClient.setClientId(getUIClientName(cr));
        uiClient.setRedirectUris(List.of("*"));
        uiClient.setWebOrigins(List.of("*"));
        uiClient.setPublicClient(true);
        uiClient.setFullScopeAllowed(true);

        uiClient.setDefaultClientScopes(List.of(
                "acr",
                "address",
                "basic",
                "email",
                "microprofile-jwt",
                "offline_access",
                "phone",
                "profile",
                "roles",

                readDocumentScope.getName(),
                createDocumentScope.getName(),
                updateDocumentScope.getName(),
                deleteDocumentScope.getName()
        ));

        return k8sClient.resource(realmImport)
                .inNamespace(cr.getMetadata().getNamespace())
                .create();
    }

    private Realm getDefaultRealm() {
        try {
            InputStream defaultRealmInputStream = KeycloakRealmService.class.getClassLoader().getResourceAsStream("realm.json");
            ObjectReader objectReader = objectMapper.readerFor(Realm.class);
            return objectReader.readValue(defaultRealmInputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void cleanupDependentResources(Trustify cr) {
        getCurrentInstance(cr).ifPresent(keycloakRealmImport -> {
            k8sClient.resource(keycloakRealmImport).delete();
        });
    }
}
