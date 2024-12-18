# VERSION defines the project version for the bundle.
# Update this value when you upgrade the version of your project.
# To re-generate a bundle for another specific version without changing the standard setup, you can:
# - use the VERSION as arg of the bundle target (e.g make bundle VERSION=0.0.2)
# - use environment variables to overwrite this value (e.g export VERSION=0.0.2)
VERSION ?= 99.0.0

# CONTAINER_RUNTIME defines the container runtime used in the Makefile to allow usage
# with docker or podman
CONTAINER_RUNTIME ?= docker

# TARGET_ARCH is the architecture of the image to be built
# Note, that even developers running on arm64 Macs will likely want to set
# this to amd64 when building local images to deploy into remote clusters
TARGET_ARCH ?= amd64

# CHANNELS define the bundle channels used in the bundle.
CHANNELS ?= "alpha"
# Add a new line here if you would like to change its default config. (E.g CHANNELS = "candidate,fast,stable")
# To re-generate a bundle for other specific channels without changing the standard setup, you can:
# - use the CHANNELS as arg of the bundle target (e.g make bundle CHANNELS=candidate,fast,stable)
# - use environment variables to overwrite this value (e.g export CHANNELS="candidate,fast,stable")
ifneq ($(origin CHANNELS), undefined)
BUNDLE_CHANNELS := --channels=$(CHANNELS)
endif

# DEFAULT_CHANNEL defines the default channel used in the bundle.
# Add a new line here if you would like to change its default config. (E.g DEFAULT_CHANNEL = "stable")
# To re-generate a bundle for any other default channel without changing the default setup, you can:
# - use the DEFAULT_CHANNEL as arg of the bundle target (e.g make bundle DEFAULT_CHANNEL=stable)
# - use environment variables to overwrite this value (e.g export DEFAULT_CHANNEL="stable")
comma := ,
space :=
space +=
DEFAULT_CHANNEL ?= $(word 1,$(subst $(comma), $(space), $(CHANNELS)))
ifneq ($(origin DEFAULT_CHANNEL), undefined)
BUNDLE_DEFAULT_CHANNEL := --default-channel=$(DEFAULT_CHANNEL)
endif
BUNDLE_METADATA_OPTS ?= $(BUNDLE_CHANNELS) $(BUNDLE_DEFAULT_CHANNEL)

IMAGE_ORG ?= ghcr.io/trustification

# IMAGE_TAG_BASE defines the docker.io namespace and part of the image name for remote images.
# This variable is used to construct full image tags for bundle and catalog images.
#
# For example, running 'make bundle-build bundle-push catalog-build catalog-push' will build and push both
# trustify.io/trustify-operator-bundle:$VERSION and trustify.io/trustify-operator-catalog:$VERSION.
IMAGE_TAG_BASE ?= $(IMAGE_ORG)/trustify-operator

# BUNDLE_IMG defines the image:tag used for the bundle.
# You can use it as an arg. (E.g make bundle-build BUNDLE_IMG=<some-registry>/<project-name-bundle>:<tag>)
BUNDLE_IMG ?= $(IMAGE_TAG_BASE)-bundle:v$(VERSION)

NAMESPACE ?= trustify

# Image URL to use all building/pushing image targets
IMG ?= $(IMAGE_ORG)/trustify-operator:latest

QUARKUS_OPTS := "-Dquarkus.container-image.image=${IMG} -Dquarkus.application.version=${VERSION} ${QUARKUS_OPTS}"

.PHONY: all
all: docker-build

##@ General

# The help target prints out all targets with their descriptions organized
# beneath their categories. The categories are represented by '##@' and the
# target descriptions by '##'. The awk commands is responsible for reading the
# entire set of makefiles included in this invocation, looking for lines of the
# file as xyz: ## something, and then pretty-format the target and help. Then,
# if there's a line with ##@ something, that gets pretty-printed as a category.
# More info on the usage of ANSI control characters for terminal formatting:
# https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_parameters
# More info on the awk command:
# http://linuxcommand.org/lc3_adv_awk.php

.PHONY: help
help: ## Display this help.
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

##@ Build

.PHONY: run
run: ## Run against the configured Kubernetes cluster in ~/.kube/config
	./mvnw compile quarkus:dev

TARGET_PLATFORMS ?= linux/${TARGET_ARCH}
CONTAINER_BUILDARGS ?= 
DOCKERFILE ?= Dockerfile
.PHONY: docker-build
docker-build: ## Build docker image with the manager.
ifeq ($(CONTAINER_RUNTIME), podman)
	$(CONTAINER_RUNTIME) build --arch ${TARGET_ARCH} -t ${IMG} ${CONTAINER_BUILDARGS} -f ${DOCKERFILE} .
else
	$(CONTAINER_RUNTIME) build --platform ${TARGET_PLATFORMS} -t ${IMG} ${CONTAINER_BUILDARGS} -f ${DOCKERFILE} .
endif

.PHONY: docker-push
docker-push: ## Push docker image with the manager.
	$(CONTAINER_RUNTIME) push ${IMG}

##@ Deployment

OS := $(shell uname -s | tr '[:upper:]' '[:lower:]')
ARCH := $(shell uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/')

OPERATOR_SDK = $(shell pwd)/bin/operator-sdk
OPERATOR_SDK_VERSION ?= v1.35.0
.PHONY: operator-sdk
operator-sdk:
ifeq (,$(wildcard $(OPERATOR_SDK)))
ifeq (,$(shell which operator-sdk 2>/dev/null))
	@{ \
	set -e ;\
	mkdir -p $(dir $(OPERATOR_SDK)) ;\
	curl -Lo $(OPERATOR_SDK) https://github.com/operator-framework/operator-sdk/releases/download/$(OPERATOR_SDK_VERSION)/operator-sdk_$(shell go env GOOS)_$(shell go env GOARCH) ;\
	chmod +x $(OPERATOR_SDK) ;\
	}
else
OPERATOR_SDK = $(shell which operator-sdk)
endif
endif

.PHONY: bundle-build
bundle-build: ## Build the bundle image.
ifeq ($(CONTAINER_RUNTIME), podman)
	$(CONTAINER_RUNTIME) build --arch ${TARGET_ARCH} -f bundle.Dockerfile -t $(BUNDLE_IMG) --build-arg QUARKUS_OPTS=${QUARKUS_OPTS} --build-arg CHANNELS=${CHANNELS} .
else
	$(CONTAINER_RUNTIME) build --platform ${TARGET_PLATFORMS} -f bundle.Dockerfile -t $(BUNDLE_IMG) --build-arg QUARKUS_OPTS=${QUARKUS_OPTS} --build-arg CHANNELS=${CHANNELS} .
endif

.PHONY: bundle-push
bundle-push: ## Push the bundle image.
	$(MAKE) docker-push IMG=$(BUNDLE_IMG)

.PHONY: opm
OPM = ./bin/opm
opm: ## Download opm locally if necessary.
ifeq (,$(wildcard $(OPM)))
ifeq (,$(shell which opm 2>/dev/null))
	@{ \
	set -e ;\
	mkdir -p $(dir $(OPM)) ;\
	curl -sSLo $(OPM) https://github.com/operator-framework/operator-registry/releases/download/v1.30.0/$(OS)-$(ARCH)-opm ;\
	chmod +x $(OPM) ;\
	}
else
OPM = $(shell which opm)
endif
endif

# A comma-separated list of bundle images (e.g. make catalog-build BUNDLE_IMGS=example.com/operator-bundle:v0.1.0,example.com/operator-bundle:v0.2.0).
# These images MUST exist in a registry and be pull-able.
BUNDLE_IMGS ?= $(BUNDLE_IMG)

# The image tag given to the resulting catalog image (e.g. make catalog-build CATALOG_IMG=example.com/operator-catalog:v0.2.0).
CATALOG_IMG ?= $(IMAGE_TAG_BASE)-catalog:v$(VERSION)

# Set CATALOG_BASE_IMG to an existing catalog image tag to add $BUNDLE_IMGS to that image.
ifneq ($(origin CATALOG_BASE_IMG), undefined)
FROM_INDEX_OPT := --from-index $(CATALOG_BASE_IMG)
endif

# Build a catalog image by adding bundle images to an empty catalog using the operator package manager tool, 'opm'.
# This recipe invokes 'opm' in 'semver' bundle add mode. For more information on add modes, see:
# https://github.com/operator-framework/community-operators/blob/7f1438c/docs/packaging-operator.md#updating-your-existing-operator
.PHONY: catalog-build
catalog-build: opm ## Build a catalog image.
	$(OPM) index add --container-tool $(CONTAINER_RUNTIME) --mode semver --tag $(CATALOG_IMG) --bundles $(BUNDLE_IMGS) $(FROM_INDEX_OPT)

# Only generate an index Dockerfile so we can create a multi-arch index
.PHONY: catalog-index
catalog-index: opm ## Generate a catalog image dockerfile.
	$(OPM) index add --container-tool $(CONTAINER_RUNTIME) --mode semver --tag $(CATALOG_IMG) --bundles $(BUNDLE_IMGS) $(FROM_INDEX_OPT) --generate

# Push the catalog image.
.PHONY: catalog-push
catalog-push: ## Push a catalog image.
	$(MAKE) docker-push IMG=$(CATALOG_IMG)

.PHONY: start-minikube
start-minikube:
	bash hack/start-minikube.sh

.PHONY: start-kind
start-kind:
	kind create cluster --config scripts/kind/config.yaml
	kubectl apply -f https://kind.sigs.k8s.io/examples/ingress/deploy-ingress-nginx.yaml

.PHONY: start-crc
start-crc:
	crc start --cpus 8 --memory 32768 --disk-size 80

.PHONY: install-trustify
install-trustify:
	bash hack/install-trustify.sh
