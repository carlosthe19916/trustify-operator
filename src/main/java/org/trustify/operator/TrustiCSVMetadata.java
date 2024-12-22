package org.trustify.operator;

import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.SharedCSVMetadata;

@CSVMetadata(
        displayName = "Trustify Operator",
        permissionRules = {
                @CSVMetadata.PermissionRule(
                        apiGroups = {""},
                        resources = {"pods", "persistentvolumeclaims", "services", "configmaps", "secrets"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"k8s.keycloak.org"},
                        resources = {"keycloaks", "keycloakrealmimports"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"operators.coreos.com"},
                        resources = {"subscriptions", "operatorgroups", "clusterserviceversions"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"route.openshift.io"},
                        resources = {"routes"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"networking.k8s.io"},
                        resources = {"ingresses"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"apps", "extensions"},
                        resources = {"deployments"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"config.openshift.io"},
                        resources = {"ingresses"},
                        verbs = {"get", "list"}
                )
        },
        installModes = {
                @CSVMetadata.InstallMode(type = "OwnNamespace", supported = true),
                @CSVMetadata.InstallMode(type = "SingleNamespace", supported = false),
                @CSVMetadata.InstallMode(type = "MultiNamespace", supported = false),
                @CSVMetadata.InstallMode(type = "AllNamespaces", supported = false)
        },
        icon = @CSVMetadata.Icon(fileName = "icon.png", mediatype = "image/png"),
        description = """
                Trustify is vendor-neutral, thought-leadering, mostly informational collection of resources devoted to making Software Supply Chains easier to create, manage, consume and ultimately... to trust!
                
                
                You can use Trustify for:
                
                - Store and Manage all the SBOM (Software Bill of Materials) files of your company.
                - Understand which are the Vulnerabilities that affect each of your SBOM files
                - Understand exactly which Packages are included/shipped within each SBOM
                
                Trustify is a project within the [Trustification community](https://trustification.io/).
                
                
                ### Install
                Once you have successfully installed the Operator, proceed to deploy components by creating the required CR.
                
                By default, the Operator installs the following components on a target cluster:
                
                * Server
                * UI
                
                ### Documentation
                Documentation can be found on our [website](https://trustification.io/).
                
                ### Getting help
                If you encounter any issues while using Trustify, you can create an issue on our [Github repo](https://github.com/trustification/trustify/issues), for bugs, enhancements or other requests.
                
                ### Contributing
                You can contribute by:
                
                * Raising any issues you find
                * Fixing issues by opening [Pull Requests](https://github.com/trustification/trustify/pulls)
                
                """,
        keywords = {"trust"},
        maturity = "alpha",
        provider = @CSVMetadata.Provider(name = "Trustification"),
        links = {
                @CSVMetadata.Link(name = "Website", url = "https://trustification.io/"),
                @CSVMetadata.Link(name = "Github", url = "https://github.com/trustification/trustify")
        },
        maintainers = {@CSVMetadata.Maintainer(name = "Trustification", email = "trustification@googlegroups.com")},
        annotations = @CSVMetadata.Annotations(
                containerImage = "ghcr.io/trustification/trustify-operator",
                repository = "https://github.com/trustification/trustify-operator",
                categories = "Application Runtime",
                capabilities = "Basic Install",
                almExamples = """                                      
                        [{
                          "apiVersion": "org.trustify/v1alpha1",
                          "kind": "Trustify",
                          "metadata": {
                            "name": "myapp"
                          },
                          "spec": { }
                        }]
                        """
        ),
        minKubeVersion = "1.23.0"
)
public class TrustiCSVMetadata implements SharedCSVMetadata {
}
