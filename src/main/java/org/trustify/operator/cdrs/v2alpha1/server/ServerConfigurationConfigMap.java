package org.trustify.operator.cdrs.v2alpha1.server;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.cdrs.v2alpha1.keycloak.services.KeycloakRealmService;
import org.trustify.operator.cdrs.v2alpha1.keycloak.utils.KeycloakUtils;
import org.trustify.operator.cdrs.v2alpha1.server.templates.AuthTemplate;
import org.trustify.operator.utils.CRDUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@KubernetesDependent(labelSelector = ServerConfigurationConfigMap.LABEL_SELECTOR, resourceDiscriminator = ServerConfigurationConfigMapDiscriminator.class)
@ApplicationScoped
public class ServerConfigurationConfigMap extends CRUDKubernetesDependentResource<ConfigMap, Trustify>
        implements Creator<ConfigMap, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    private static final Logger logger = Logger.getLogger(ServerConfigurationConfigMap.class);

    public ServerConfigurationConfigMap() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(Trustify cr, Context<Trustify> context) {
        return newConfigMap(cr, context);
    }

    @SuppressWarnings("unchecked")
    private ConfigMap newConfigMap(Trustify cr, Context<Trustify> context) {
        AtomicReference<String> yamlFile = new AtomicReference<>();

        Optional.ofNullable(cr.getSpec().oidcSpec())
                .ifPresent((oidcSpec) -> {
                    if (oidcSpec.enabled()) {
                        TrustifySpec.OidcProviderType providerType = Objects.nonNull(oidcSpec.type()) ? oidcSpec.type() : TrustifySpec.OidcProviderType.EMBEDDED;
                        switch (providerType) {
                            case EXTERNAL -> {
                                if (oidcSpec.externalOidcSpec() != null) {
                                    AuthTemplate.Data data = new AuthTemplate.Data(List.of(
                                            new AuthTemplate.Client(
                                                    oidcSpec.externalOidcSpec().serverUrl(),
                                                    oidcSpec.externalOidcSpec().uiClientId(),
                                                    Collections.emptyList()
                                            ),
                                            new AuthTemplate.Client(
                                                    oidcSpec.externalOidcSpec().serverUrl(),
                                                    oidcSpec.externalOidcSpec().serverClientId(),
                                                    Collections.emptyList()
                                            )
                                    ));
                                    yamlFile.set(AuthTemplate.auth(data).render());
                                } else {
                                    logger.error("Oidc provider type is EXTERNAL but no config for external oidc was provided");
                                }
                            }
                            case EMBEDDED -> {
                                AuthTemplate.Data data = new AuthTemplate.Data(List.of(
                                        new AuthTemplate.Client(
                                                KeycloakUtils.serverUrlWithRealmIncluded(cr),
                                                KeycloakRealmService.getUIClientName(cr),
                                                Collections.singletonList(getConfigMapOidcTlsExpectedPath())
                                        ),
                                        new AuthTemplate.Client(
                                                KeycloakUtils.serverUrlWithRealmIncluded(cr),
                                                KeycloakRealmService.getBackendClientName(cr),
                                                Collections.singletonList(getConfigMapOidcTlsExpectedPath())
                                        )
                                ));
                                yamlFile.set(AuthTemplate.auth(data).render());
                            }
                        }
                    }
                });

        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(getConfigMapName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .addToLabels("component", "server")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withData(Map.of(
                        getConfigMapAuthKey(cr), "\n" + (yamlFile.get() != null ? yamlFile.get() : "")
                ))
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

    public static String getConfigMapAuthKey(Trustify cr) {
        return "auth.yaml";
    }

    public static String getConfigMapOidcTlsExpectedPath() {
        return "/etc/trust-anchor/tls.crt";
    }
}
