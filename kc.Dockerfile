FROM registry.access.redhat.com/ubi9/nodejs-20:latest as theme-builder

USER root
RUN dnf install -y maven

USER 1001
COPY --chown=1001 ./keycloak/ .
RUN npm install -g npm@9
RUN pwd
RUN npm clean-install --ignore-scripts && npm run build-keycloak-theme

FROM quay.io/keycloak/keycloak:latest as kc-builder
COPY --chown=keycloak --from=theme-builder /opt/app-root/src/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar /opt/keycloak/providers/

ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
ENV KC_DB=postgres
ENV KC_HTTP_RELATIVE_PATH=/auth
ENV KC_HTTP_MANAGEMENT_RELATIVE_PATH=/auth

WORKDIR /opt/keycloak

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:latest
COPY --from=kc-builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
