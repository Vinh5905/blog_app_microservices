#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export MAVEN_USER_HOME="${ROOT_DIR}/.cache/maven"
export MAVEN_OPTS="-Dmaven.repo.local=${ROOT_DIR}/.cache/maven/repository ${MAVEN_OPTS:-}"
export NPM_CONFIG_CACHE="${ROOT_DIR}/.cache/npm"

for service in api-gateway-server userauthservice postservice commentservice; do
  echo "==> Verifying ${service}"
  (cd "${ROOT_DIR}/${service}" && ./mvnw -B -ntp clean verify)
done

echo "==> Verifying blog-client"
(cd "${ROOT_DIR}/blog-client" && npm ci && CI=true npm test && npm run build)
