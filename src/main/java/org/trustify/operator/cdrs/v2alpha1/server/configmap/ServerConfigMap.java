package org.trustify.operator.cdrs.v2alpha1.server.configmap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.keycloak.k8s.v2alpha1.Keycloak;
import org.trustify.operator.Constants;
import org.trustify.operator.cdrs.v2alpha1.Trustify;
import org.trustify.operator.cdrs.v2alpha1.TrustifySpec;
import org.trustify.operator.services.KeycloakRealmService;
import org.trustify.operator.services.KeycloakServerService;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@KubernetesDependent(labelSelector = ServerConfigMap.LABEL_SELECTOR, resourceDiscriminator = ServerConfigMapDiscriminator.class)
@ApplicationScoped
public class ServerConfigMap extends CRUDKubernetesDependentResource<ConfigMap, Trustify>
        implements Creator<ConfigMap, Trustify> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=trustify-operator,component=server";

    private static final Logger logger = Logger.getLogger(ServerConfigMap.class);

    @Inject
    KubernetesClient k8sClient;

    public ServerConfigMap() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(Trustify cr, Context<Trustify> context) {
        return newConfigMap(cr, context);
    }

    @Override
    public Result<ConfigMap> match(ConfigMap actual, Trustify cr, Context<Trustify> context) {
        boolean match = Objects.equals(getAuthValue(cr, context), actual.getData().get(getAuthKey(cr)));
        return Result.nonComputed(match);
    }

    public static String getAuthKey(Trustify cr) {
        return "auth.yaml";
    }

    public String getAuthValue(Trustify cr, Context<Trustify> context) {
        Optional<String> yamlFile = Optional.ofNullable(cr.getSpec().oidcSpec())
                .flatMap(oidcSpec -> {
                    if (oidcSpec.enabled()) {
                        Optional<String> oidcSecretName = oidcSpec.externalServer() ?
                                Optional.ofNullable(oidcSpec.externalOidcSpec()).map(TrustifySpec.ExternalOidcSpec::tlsSecret) :
                                Optional.ofNullable(oidcSpec.embeddedOidcSpec()).map(TrustifySpec.EmbeddedOidcSpec::tlsSecret);

                        Optional<Secret> oidcSecret = oidcSecretName.map(name -> {
                            Secret secret = new SecretBuilder()
                                    .withNewMetadata()
                                    .withName(name)
                                    .endMetadata()
                                    .build();
                            return k8sClient.resource(secret)
                                    .inNamespace(cr.getMetadata().getNamespace())
                                    .get();
                        });


                        if (oidcSpec.externalServer()) {
                            if (oidcSpec.externalOidcSpec() != null) {
                                AuthTemplate.Data data = new AuthTemplate.Data(List.of(new AuthTemplate.Client(
                                        oidcSpec.externalOidcSpec().serverUrl(),
                                        oidcSpec.externalOidcSpec().uiClientId(),
                                        oidcSecret.isPresent() ? List.of(getAuthTlsCaCertificatePath(cr)) : Collections.emptyList()
                                )));
                                return Optional.of(AuthTemplate.auth(data).render());
                            } else {
                                logger.error("Oidc provider type is EXTERNAL but no config for external oidc was provided");
                                return Optional.empty();
                            }
                        } else {
                            AtomicReference<Keycloak> keycloakInstance = context.managedDependentResourceContext().getMandatory(Constants.KEYCLOAK, AtomicReference.class);

                            String keycloakRelativePath = KeycloakRealmService.getRealmClientRelativePath(cr);
                            String serverUrl = KeycloakServerService.getServiceUrl(cr, keycloakInstance.get()) + keycloakRelativePath;

                            AuthTemplate.Data data = new AuthTemplate.Data(List.of(new AuthTemplate.Client(
                                    serverUrl,
                                    KeycloakRealmService.getUIClientName(cr),
                                    oidcSecret.isPresent() ? List.of(getAuthTlsCaCertificatePath(cr)) : Collections.emptyList()
                            )));
                            return Optional.of(AuthTemplate.auth(data).render());
                        }
                    }
                    return Optional.empty();
                });
        return "\n" + yamlFile.orElse("");
    }

    private ConfigMap newConfigMap(Trustify cr, Context<Trustify> context) {
        return new ConfigMapBuilder()
                .withMetadata(Constants.metadataBuilder
                        .apply(new Constants.Resource(getConfigMapName(cr), LABEL_SELECTOR, cr))
                        .build()
                )
                .withData(Map.of(
                        getAuthKey(cr), getAuthValue(cr, context))
                )
                .build();
    }

    public static String getConfigMapName(Trustify cr) {
        return cr.getMetadata().getName() + Constants.SERVER_CONFIG_MAP_SUFFIX;
    }

    public static String getAuthTlsCaCertificateDirectory(Trustify cr) {
        return "/opt/trustify/tls-oidc";
    }

    public static String getAuthTlsCaCertificatePath(Trustify cr) {
        return getAuthTlsCaCertificateDirectory(cr) + "/tls.crt";
    }
}
