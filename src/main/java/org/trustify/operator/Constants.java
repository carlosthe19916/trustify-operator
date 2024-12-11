package org.trustify.operator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.utils.CRDUtils;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class Constants {
    public static final String CRDS_GROUP = "org.trustify";
    public static final String CRDS_VERSION = "v1alpha1";

    public static final String CLUSTER_SERVICE = "kubernetesCluster";
    public static final String CONTEXT_KEYCLOAK_SERVER_SERVICE_KEY = "keycloakServerService";
    public static final String CONTEXT_KEYCLOAK_REALM_SERVICE_KEY = "keycloakRealmService";

    public record Resource(String name, String labelSelector, Trustify cr) {
    }

    public static Function<Resource, ObjectMetaBuilder> metadataBuilder = resource -> {
        Map<String, String> labels = Map.of(
                "app.kubernetes.io/name", resource.cr.getMetadata().getName(),
                "app.kubernetes.io/part-of", resource.cr.getMetadata().getName(),
                "trustify-operator/cluster", Constants.TRUSTI_NAME
        );
        Map<String, String> labelSelector = CRDUtils.getLabelsFromString(resource.labelSelector);

        return new ObjectMetaBuilder()
                .withName(resource.name)
                .withNamespace(resource.cr.getMetadata().getNamespace())
                .withAnnotations(Collections.emptyMap())
                .addToLabels(labels)
                .addToLabels(labelSelector)
                .withOwnerReferences(CRDUtils.getOwnerReference(resource.cr));
    };

    //
    public static final String TRUSTI_NAME = "trustify";
    public static final String TRUSTI_UI_NAME = "trustify-ui";
    public static final String TRUSTI_SERVER_NAME = "trustify-server";
    public static final String TRUSTI_DB_NAME = "trustify-db";

    public static final String KEYCLOAK_NAME = "keycloak";
    public static final String KEYCLOAK_DB_NAME = KEYCLOAK_NAME + "-db";

    //
    public static final String SERVICE_PROTOCOL = "TCP";

    //
    public static final String DB_PVC_SUFFIX = "-" + TRUSTI_DB_NAME + "-pvc";
    public static final String DB_SECRET_SUFFIX = "-" + TRUSTI_DB_NAME + "-secret";
    public static final String DB_DEPLOYMENT_SUFFIX = "-" + TRUSTI_DB_NAME + "-deployment";
    public static final String DB_SERVICE_SUFFIX = "-" + TRUSTI_DB_NAME + "-service";

    public static final String UI_DEPLOYMENT_SUFFIX = "-" + TRUSTI_UI_NAME + "-deployment";
    public static final String UI_SERVICE_SUFFIX = "-" + TRUSTI_UI_NAME + "-service";

    public static final String SERVER_PVC_SUFFIX = "-" + TRUSTI_SERVER_NAME + "-pvc";
    public static final String SERVER_DEPLOYMENT_SUFFIX = "-" + TRUSTI_SERVER_NAME + "-deployment";
    public static final String SERVER_SERVICE_SUFFIX = "-" + TRUSTI_SERVER_NAME + "-service";

    public static final String OIDC_DB_PVC_SUFFIX = "-" + KEYCLOAK_DB_NAME + "-pvc";
    public static final String OIDC_DB_SECRET_SUFFIX = "-" + KEYCLOAK_DB_NAME + "-secret";
    public static final String OIDC_DB_DEPLOYMENT_SUFFIX = "-" + KEYCLOAK_DB_NAME + "-deployment";
    public static final String OIDC_DB_SERVICE_SUFFIX = "-" + KEYCLOAK_DB_NAME + "-service";

    public static final String INGRESS_SUFFIX = "-" + TRUSTI_NAME + "-ingress";

    //
    public static final String DB_SECRET_USERNAME = "username";
    public static final String DB_SECRET_PASSWORD = "password";
    public static final String DB_NAME = "trustify";
    public static final Integer DB_PORT = 5432;
}
