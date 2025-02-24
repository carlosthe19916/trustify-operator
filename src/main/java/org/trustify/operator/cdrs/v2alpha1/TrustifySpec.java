package org.trustify.operator.cdrs.v2alpha1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.SecretKeySelector;

import java.util.List;
import java.util.Map;

public record TrustifySpec(
        @JsonPropertyDescription("Custom Trustify UI image to be used. For internal use only")
        String uiImage,

        @JsonPropertyDescription("Custom Trustify Server image to be used. For internal use only")
        String serverImage,

        @JsonPropertyDescription("Custom Trustify DB Server image to be used. For internal use only")
        String dbImage,

        @JsonPropertyDescription("Custom Image Pull Policy for images managed by the Operator")
        String imagePullPolicy,

        @JsonPropertyDescription("Secret(s) that might be used when pulling an image from a private container image registry or repository.")
        List<LocalObjectReference> imagePullSecrets,

        @JsonPropertyDescription("Number of UI instances. Default is 1.")
        Integer uiInstances,

        @JsonPropertyDescription("Number of Server instances. Default is 1.")
        Integer serverInstances,

        @JsonPropertyDescription("Number of Importer instances. Default is 1.")
        Integer importerInstances,

        @JsonPropertyDescription("Size of the PVC for each importer to use")
        String importerWorkdirPvcSize,

        @JsonProperty("http")
        @JsonPropertyDescription("In this section you can configure features related to HTTP and HTTPS")
        HttpSpec httpSpec,

        @JsonProperty("db")
        @JsonPropertyDescription("In this section you can find all properties related to connect to a database.")
        DatabaseSpec databaseSpec,

        @JsonProperty("hostname")
        @JsonPropertyDescription("In this section you can configure hostname and related properties.")
        HostnameSpec hostnameSpec,

        @JsonProperty("oidc")
        @JsonPropertyDescription("In this section you can configure Oidc settings.")
        OidcSpec oidcSpec,

        @JsonProperty("storage")
        @JsonPropertyDescription("In this section you can configure Storage settings.")
        StorageSpec storageSpec,

        @JsonProperty("uiResources")
        @JsonPropertyDescription("In this section you can configure resource limits settings for the UI.")
        ResourcesLimitSpec uiResourceLimitSpec,

        @JsonProperty("serverResources")
        @JsonPropertyDescription("In this section you can configure resource limits settings for the Server.")
        ResourcesLimitSpec serverResourceLimitSpec,

        @JsonProperty("importerResources")
        @JsonPropertyDescription("In this section you can configure resource limits settings for the Importer.")
        ResourcesLimitSpec importerResourceLimitSpec
) {

    public TrustifySpec() {
        this(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public record ExternalDatabaseSpec(
            @JsonPropertyDescription("The reference to a secret holding the username of the database user.")
            SecretKeySelector usernameSecret,

            @JsonPropertyDescription("The reference to a secret holding the password of the database user.")
            SecretKeySelector passwordSecret,

            @JsonPropertyDescription("The host of the database.")
            String host,

            @JsonPropertyDescription("The port of the database.")
            String port,

            @JsonPropertyDescription("The database name.")
            String name,

            @JsonPropertyDescription("The minimal size of the connection pool.")
            Integer poolMinSize,

            @JsonPropertyDescription("The maximum size of the connection pool.")
            Integer poolMaxSize,

            @JsonPropertyDescription("default: prefer [possible values: disable, allow, prefer, require, verify-ca, verify-full]")
            String sslMode
    ) {
    }

    public record EmbeddedDatabaseSpec(
            @JsonPropertyDescription("Size of the PVC to create. Valid only if externalDatabase=false")
            String pvcSize,

            @JsonProperty("resources")
            @JsonPropertyDescription("In this section you can configure resource limits settings. Valid only if externalDatabase=false")
            ResourcesLimitSpec resourceLimits
    ) {
    }

    public record DatabaseSpec(
            @JsonPropertyDescription("Use external database.")
            boolean externalDatabase,

            @JsonProperty("external")
            ExternalDatabaseSpec externalDatabaseSpec,

            @JsonProperty("embedded")
            EmbeddedDatabaseSpec embeddedDatabaseSpec
    ) {
    }

    public record HostnameSpec(
            @JsonPropertyDescription("Hostname for the server.")
            String hostname
    ) {
    }

    public record HttpSpec(
            @JsonPropertyDescription("A secret containing the TLS configuration for HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.")
            String tlsSecret
    ) {
    }

    public record IngressSpec(
            @JsonProperty("enabled")
            boolean ingressEnabled,

            @JsonProperty("className")
            String ingressClassName,

            @JsonProperty("annotations")
            @JsonPropertyDescription("Additional annotations to be appended to the Ingress object")
            Map<String, String> annotations
    ) {
    }

    public record ExternalOidcDatabaseSpec(
            @JsonPropertyDescription("The database vendor. E.g. 'postgres'")
            String vendor,

            @JsonPropertyDescription("The reference to a secret holding the username of the database user.")
            SecretKeySelector usernameSecret,

            @JsonPropertyDescription("The reference to a secret holding the password of the database user.")
            SecretKeySelector passwordSecret,

            @JsonPropertyDescription("The host of the database.")
            String host,

            @JsonPropertyDescription("The port of the database.")
            String port,

            @JsonPropertyDescription("The database name.")
            String name
    ) {
    }

    public record EmbeddedOidcSpec(
            @JsonPropertyDescription("A secret containing the TLS configuration for OIDC - HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.")
            String tlsSecret,

            @JsonProperty("db")
            @JsonPropertyDescription("In this section you can find all properties related to connect to a database.")
            EmbeddedOidcDatabaseSpec databaseSpec
    ) {
    }

    public record EmbeddedOidcDatabaseSpec(
            @JsonPropertyDescription("Use external database.")
            boolean externalDatabase,

            @JsonProperty("external")
            ExternalOidcDatabaseSpec externalDatabaseSpec,

            @JsonProperty("embedded")
            EmbeddedDatabaseSpec embeddedDatabaseSpec
    ) {
    }

    public record ExternalOidcSpec(
            @JsonPropertyDescription("Oidc server url.")
            String serverUrl,
            @JsonPropertyDescription("Oidc client id for the UI.")
            String uiClientId,
            @JsonPropertyDescription("A secret containing the TLS configuration for OIDC - HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.")
            String tlsSecret
    ) {
    }

    public record OidcSpec(
            @JsonPropertyDescription("Enable Auth.")
            boolean enabled,

            @JsonPropertyDescription("Whether or not the user will provide its own OIDC Server. If 'false', the operator will provide a OIDC Server")
            boolean externalServer,

            @JsonProperty("external")
            ExternalOidcSpec externalOidcSpec,

            @JsonProperty("embedded")
            EmbeddedOidcSpec embeddedOidcSpec
    ) {
    }

    public enum StorageStrategyType {
        FILESYSTEM("fs"),
        S3("s3");
        private final String value;

        StorageStrategyType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum StorageCompressionType {
        NONE("none"),
        ZSTD("zstd");
        private final String value;

        StorageCompressionType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public record StorageSpec(
            @JsonPropertyDescription("Storage compression.")
            StorageCompressionType compression,
            @JsonPropertyDescription("Storage type.")
            StorageStrategyType type,
            @JsonProperty("filesystem")
            FilesystemStorageSpec filesystemStorageSpec,
            @JsonProperty("s3")
            S3StorageSpec s3StorageSpec
    ) {
    }

    public record FilesystemStorageSpec(
            @JsonPropertyDescription("Size of the PVC to create.")
            String pvcSize
    ) {
    }

    public record S3StorageSpec(
            @JsonPropertyDescription("Region name.")
            String region,
            @JsonPropertyDescription("Bucket name.")
            String bucket,
            @JsonPropertyDescription("Access key.")
            String accessKey,
            @JsonPropertyDescription("Secret key.")
            String secretKey
    ) {
    }

    public record ResourcesLimitSpec(
            @JsonPropertyDescription("Requested CPU.")
            String cpuRequest,

            @JsonPropertyDescription("Limit CPU.")
            String cpuLimit,

            @JsonPropertyDescription("Requested memory.")
            String memoryRequest,

            @JsonPropertyDescription("Limit Memory.")
            String memoryLimit
    ) {
    }

}