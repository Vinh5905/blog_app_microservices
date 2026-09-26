#!/usr/bin/env bash
# Build once; scanning and publishing must use these exact image IDs.
set -euo pipefail
: "${GITHUB_SHA:?}" "${GITHUB_REPOSITORY:?}" "${RUNNER_TEMP:?}"
python3 scripts/trivy_inputs.py
evidence="$RUNNER_TEMP/trivy-images"
mkdir -p "$evidence"
printf '%s\n' "$GITHUB_SHA" > "$evidence/source-sha.txt"
trivy version --format json > "$evidence/trivy-version.json"
status=0
refs=()
for module in blog-client api-gateway-server userauthservice postservice commentservice; do
  ref="blogapp-ci/${module}:${GITHUB_SHA}"
  refs+=("$ref")
  docker build --platform linux/amd64 --label "org.opencontainers.image.source=https://github.com/$GITHUB_REPOSITORY" \
    --label "org.opencontainers.image.revision=$GITHUB_SHA" --tag "$ref" "$module"
  docker image inspect --format='{{.Id}}' "$ref" > "$evidence/${module}.image-id"
  trivy image --config security/trivy/trivy.yaml --ignorefile /dev/null --image-src docker --scanners vuln --list-all-pkgs --timeout 20m \
    --format json --output "$evidence/${module}.json" "$ref"
  python3 scripts/trivy_policy.py --stage image --module "$module" --expected-image "$ref" \
    --image-id-file "$evidence/${module}.image-id" --mode enforce \
    --report "$evidence/${module}.json" --summary "$evidence/${module}.summary.json" || status=1
  trivy convert --scanners vuln --format cyclonedx --output "$evidence/${module}.cdx.json" "$evidence/${module}.json"
done
trivy version --format json > "$evidence/trivy-version.json"
test "$status" -eq 0
# PRs cannot export candidates for a later privileged workflow.
if [[ "${GITHUB_EVENT_NAME:-}" == push && "${GITHUB_REF:-}" == refs/heads/main ]]; then
  docker image save --output "$RUNNER_TEMP/release-images.tar" "${refs[@]}"
fi
