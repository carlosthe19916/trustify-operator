# Quick Start - Helm Chart

If you want to give a quick try of the operator without the whole glory of OLM you can deploy our Operator using Helm.

- Start minikube:

```shell
minikube start --addons=ingress,dashboard
```

- Install the Helm Chart:

```shell
helm install myhelm helm/
```

- Create an instance of Trustify:

```shell
cat << EOF | kubectl apply -f -
apiVersion: "org.trustify/v1alpha1"
kind: "Trustify"
metadata:
  name: myapp
spec: { }
EOF
```

# Local development

## Minikube

- Start minikube:

```shell
minikube start --addons=ingress,dashboard
curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.30.0/install.sh | bash -s v0.30.0
```

- Setup ServiceAccount + Role + RoleBinding:

```shell
kubectl apply -f scripts/rbac.yaml
```

- Start server in dev mode

```shell
mvn compile quarkus:dev
```

- Create an instance of the operator:

```shell
kubectl apply -f scripts/trustify.yaml
```

At this point the container images will be generated by the operator.

# Test Operator

```shell
export IMG=quay.io/${USER}/trustify-operator:v0.0.0
export BUNDLE_IMG=quay.io/${USER}/trustify-operator-bundle:v0.0.0
export CATALOG_IMG=quay.io/${USER}/trustify-operator-catalog:v0.0.0
```

- Create operator:

```shell
make docker-build docker-push
```

- Create bundle:

```shell
make bundle-build bundle-push
```

- Create catalog:

```shell
make catalog-build catalog-push
```

### Instantiate Catalog

- If you are using Minikube:

```shell
CATALOG_NAMESPACE=olm
```

- If you are using OCP:

```shell
CATALOG_NAMESPACE=openshift-marketplace
```

- Instantiate catalog:

```shell
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: trustify-catalog-source
  namespace: $CATALOG_NAMESPACE
spec:
  sourceType: grpc
  image: $CATALOG_IMG
EOF
```

At this point you can see the Operator in the marketplace of OCP ready for you to test it.

### Create subscription

- Create namespace:

```shell
kubectl create ns trustify
```

- Create group

```shell
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1
kind: OperatorGroup
metadata:
  name: operatorgroup
  namespace: trustify
spec:
  targetNamespaces:
    - trustify
EOF
```

- Create subscription:

```shell
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: trustify-subscription
  namespace: trustify
spec:
  channel: alpha
  name: trustify-operator
  source: trustify-catalog-source
  sourceNamespace: ${CATALOG_NAMESPACE}
EOF
```

### Instantiate Trustify

```shell
cat <<EOF | kubectl apply -n trustify -f -
apiVersion: "org.trustify/v1alpha1"
kind: "Trustify"
metadata:
  name: myapp
spec: { }
EOF
```

# Kubernetes & OCP version compatibility

| Red Hat OpenShift version | Kubernetes version |
---------------------------|-------------------- 
 4.15                      | 1.28               
 4.14                      | 1.27               
 4.13                      | 1.26               
 4.12                      | 1.25               
 4.11                      | 1.24               
 4.10                      | 1.23               

References:

- [What version of the Kubernetes API is included with each OpenShift 4.x release? - Red Hat Customer Portal](https://access.redhat.com/solutions/4870701)
