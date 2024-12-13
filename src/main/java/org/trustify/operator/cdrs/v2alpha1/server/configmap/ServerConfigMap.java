package org.trustify.operator.cdrs.v2alpha1.server.configmap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.keycloak.k8s.v2alpha1.Keycloak;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.services.KeycloakRealmService;
import org.trustify.operator.services.KeycloakServerService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@KubernetesDependent(labelSelector = ServerConfigMap.LABEL_SELECTOR, resourceDiscriminator = ServerConfigMapDiscriminator.class)
@ApplicationScoped
public class ServerConfigMap extends CRUDKubernetesDependentResource<ConfigMap, Trustify>
        implements Creator<ConfigMap, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    private static final Logger logger = Logger.getLogger(ServerConfigMap.class);

    public ServerConfigMap() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(Trustify cr, Context<Trustify> context) {
        return newConfigMap(cr, context);
    }

    private ConfigMap newConfigMap(Trustify cr, Context<Trustify> context) {
        Optional<String> yamlFile = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> {
                    if (oidcSpec.enabled()) {
                        TrustifySpec.OidcProviderType providerType = Objects.nonNull(oidcSpec.type()) ? oidcSpec.type() : TrustifySpec.OidcProviderType.EMBEDDED;
                        switch (providerType) {
                            case EXTERNAL -> {
                                if (oidcSpec.externalOidcSpec() != null) {
                                    AuthTemplate.Data data = new AuthTemplate.Data(List.of(new AuthTemplate.Client(
                                            oidcSpec.externalOidcSpec().serverUrl(),
                                            oidcSpec.externalOidcSpec().uiClientId()
                                    )));
                                    return Optional.of(AuthTemplate.auth(data).render());
                                } else {
                                    logger.error("Oidc provider type is EXTERNAL but no config for external oidc was provided");
                                    return Optional.empty();
                                }
                            }
                            case EMBEDDED -> {
                                final var keycloakInstance = context.managedDependentResourceContext().getMandatory(Constants.KEYCLOAK, Keycloak.class);

                                String protocol = keycloakInstance.getSpec().getHttp().getHttpEnabled() ? "http" : "https";
                                int port = keycloakInstance.getSpec().getHttp().getHttpEnabled() ? 8080 : 8443;
                                String keycloakRelativePath = KeycloakServerService.RELATIVE_PATH + "/realms/" + KeycloakRealmService.getRealmName(cr);
                                String serverUrl = String.format("%s://%s:%s%s", protocol, KeycloakServerService.getServiceHost(cr), port, keycloakRelativePath);

                                AuthTemplate.Data data = new AuthTemplate.Data(List.of(new AuthTemplate.Client(
                                        serverUrl,
                                        KeycloakRealmService.getUIClientName(cr)
                                )));
                                return Optional.of(AuthTemplate.auth(data).render());
                            }
                        }
                    }
                    return Optional.empty();
                });


        return new ConfigMapBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getConfigMapName(cr), LABEL_SELECTOR, cr))
                        .build()
                )
                .withData(Map.of(
                        getAuthKey(cr), "\n" + yamlFile.orElse(""))
                )
                .build();
    }

    @Override
    public Result<ConfigMap> match(ConfigMap actual, Trustify cr, Context<Trustify> context) {
        final var desiredConfigMap = getConfigMapName(cr);
        return Result.nonComputed(actual
                .getMetadata()
                .getName()
                .equals(desiredConfigMap)
        );
    }

    public static String getConfigMapName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.SERVER_CONFIG_MAP_SUFFIX;
    }

    public static String getAuthKey(Trustify cr) {
        return "auth.yaml";
    }

}
