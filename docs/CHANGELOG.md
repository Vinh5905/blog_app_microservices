# Source remediation history

This is the concise handoff log for implementation work. The architecture source of truth remains `Kien-truc-DevOps-BlogApp copy.md`.

## 2026-09-26 — Remediate dependencies and enforce Trivy release gates

- Changed: upgraded four independent Java modules to Boot 3.5.16/Cloud 2025.0.3 with Netty/Tomcat/BouncyCastle fixes and the new gateway route namespace; replaced CRA with Vite/Vitest and two login tests; pinned container bases and patched frontend libexpat. Source scans include dev dependencies. CI adds frontend and enforcing five-image gates on PRs, main-only GHCR digest publishing with Cosign signatures/SBOM/provenance verification, completion manifests, and daily published-digest rescans. Added architecture decision and owner handoff; no security exceptions were added.
- Verified: `bash scripts/verify.sh` with JDK 17 passed all four Maven clean verifies, JaCoCo checks, two frontend tests and Vite build. `docker compose --project-name blogapp-trivy-hardening --env-file <temporary-env> up --build -d` produced six healthy containers; `bash scripts/smoke-test.sh` passed same-origin API, malformed JWT and ownership checks, including after the libexpat rebuild. Trivy 0.74.0 source scan with `--include-dev-deps`, config scan and five exact-image-ID scans passed enforce policy: 0 fixable HIGH/CRITICAL source/image findings, 0 HIGH/CRITICAL config findings, 0 suppressed; five CycloneDX SBOMs generated. `python -m unittest discover -s scripts -p 'test_*.py' -v` passed 16 policy/release/rescan tests; actionlint and `bash -n scripts/build-scan-images.sh` passed.
- Pending: actual GitHub PR verification; owner activation and negative acceptance of required Trivy/frontend checks (current account has push but no admin); first trusted main GHCR/Cosign release and scheduled-rescan run. Mocked publication tests are not registry/signature execution evidence. GitOps/CD and other security tools remain separate architecture milestones.

## 2026-09-25 — Prepare Maven cache for Trivy on GitHub

- Changed: source scanning waits for the backend gate, restores the gateway Maven cache, resolves all four modules before scanning, and records scanner/source metadata before dependency resolution. Missing Maven metadata uses Google's public Maven Central mirror through scanner-specific configuration; merged main's Sonar integration into the Trivy branch.
- Verified: `./mvnw -B -ntp dependency:go-offline -DskipTests` passed for all four modules with JDK 17; `bash scripts/verify.sh` passed on the merged branch after starting Docker. GitHub run [36143792724](https://github.com/Vinh5905/blog_app_microservices/actions/runs/36143792724) passed backend/Sonar gates, both Trivy scans, Compose validation and artifact upload; source policy correctly failed on 244 fixable HIGH/CRITICAL findings, config had 0. The mirror resolved the HTTP 429 failures seen in prior runs. Added six policy regression tests and post-scan database metadata recording to CI; local tests and actionlint passed.
- Verified: final code commit 471a852 passed backend/Sonar and Trivy execution/policy tests in PR run 36144756912; its source gate correctly failed on 244 findings. Manual run 36144854263 passed the Trivy image baseline job; downloaded artifacts contained five matching image IDs, five CycloneDX SBOMs and 186 findings. The overall manual run is not green: source policy fails, and the inherited Sonar condition skips analysis on a non-main manual run while its aggregate gate fails.
- Pending: remediate 244 source findings and 186 image findings; complete release/ruleset acceptance criteria.

## 2026-09-25 — Added Trivy source and image baseline CI

- Changed: added pinned Trivy source/config PR checks, a five-image post-merge audit and CycloneDX artifacts, a fail-closed exception policy with tests, explicit non-root frontend Docker user, LF checkout rules, and `docs/TRIVY-PRODUCTION-CI.md`.
- Verified: `bash scripts/verify.sh` with JDK 17 passed four independent Maven verifies plus frontend test/build; `docker compose --project-name blogapp-trivy-verify --env-file <temporary-env> up --build -d` made all six containers healthy, and `bash scripts/smoke-test.sh` passed. Local Trivy v0.74.0 scanned five source manifests (244 fixable HIGH/CRITICAL), five Dockerfiles (0 HIGH/CRITICAL), and five built image IDs (186 fixable HIGH/CRITICAL); five CycloneDX SBOMs were generated. Six policy unit tests, `actionlint` on backend CI, and `docker compose config --quiet` passed. The source enforce policy correctly failed on the measured debt.
- Pending: remediate source/image findings, verify actual GitHub Actions runs and exception expiry behavior on PRs, then enable required checks and implement the digest-based GHCR release gate. SonarQube Cloud remains unconfigured.
## 2026-09-25 — Prepared Sonar integration branch with documented tests

- Changed: added purpose-and-risk comments to 31 Java test methods across the four services; carried the validated Sonar CI configuration into a separate integration branch.
- Verified: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin:$PATH ./scripts/verify.sh` passed four independent Maven builds, 31 Java tests, JaCoCo checks, and the frontend production build; frontend currently has no tests. `git diff --check` passed.
- Pending: review and merge the integration PR, confirm backend Sonar analysis on `main`, and run the manual frontend baseline.

## 2026-09-25 — Enabled required backend and Sonar checks

- Changed: activated the `main` ruleset requiring a PR, `Sonar quality gate`, and `Backend test gate` from GitHub Actions.
- Verified: PR #2 run 36094511394 passed four backend verifies, four Sonar scans, four SonarQube Cloud checks, and both aggregate gates; GitHub shows ruleset 23979594 Active for `main`.
- Pending: create a review-ready integration PR after validation, confirm a baseline run on `main`, and run the manual frontend baseline.

## 2026-09-25 — Added SonarQube Cloud CI configuration

- Changed: mapped the five supplied Sonar project keys; added four backend scans after JaCoCo and a failing aggregate Sonar gate; added a manual frontend baseline scan and documented the required GitHub secret/variable.
- Verified: both workflow YAML files parsed; all four project mappings and the aggregate gate were checked; `./scripts/verify.sh` passed four independent Maven builds, JaCoCo checks and the frontend production build with local network/Docker access; all four JaCoCo XML and bytecode inputs exist.
- Pending: run real Sonar analyses on main and an internal PR, inspect scope/coverage, then enable required checks; frontend tests and LCOV remain pending.

## 2026-09-25 — Documented API Gateway tests

- Changed: added purpose-and-risk comments to all 5 API Gateway test methods covering context startup, auth/post/comment routing, path and query preservation, upstream response propagation, route isolation, and unknown paths.
- Verified: `cd api-gateway-server && ./mvnw -B -ntp clean verify` passed 1 context test and 4 WireMock route integration tests; the JaCoCo report was generated.
- Pending: none.

## 2026-09-25 — Documented comment service tests

- Changed: added purpose-and-risk comments to all 8 comment service test methods covering timestamps, post filtering, ordering, ownership, validation, JWT contracts, and missing resources.
- Verified: `cd commentservice && ./mvnw -B -ntp clean verify` passed 5 unit tests and 3 MySQL 8.4 Testcontainers integration tests; the JaCoCo coverage checks passed.
- Pending: none.

## 2026-09-25 — Documented post service tests

- Changed: added purpose-and-risk comments to all 7 post service test methods covering timestamps, ownership, validation, JWT contracts, feed ordering, and missing resources.
- Verified: `cd postservice && ./mvnw -B -ntp clean verify` passed 4 unit tests and 3 MySQL 8.4 Testcontainers integration tests; the JaCoCo coverage checks passed.
- Pending: none.

## 2026-09-25 — Documented user authentication tests

- Changed: added purpose-and-risk comments to all 11 test methods covering JWT behavior, user/role service logic, HTTP authentication contracts, and the V1-to-latest Flyway migration.
- Verified: `cd userauthservice && ./mvnw -B -ntp clean verify` passed 7 unit tests and 4 MySQL 8.4 Testcontainers integration tests; the JaCoCo coverage checks passed.
- Pending: none.

## 2026-09-25 — Designed SonarQube Cloud CI gate

- Changed: added `docs/SONARQUBE-CLOUD-CI-DESIGN.md` for five monorepo projects, staged backend/frontend Quality Gates, coverage imports, CI checks, token boundaries, failure handling, and acceptance evidence.
- Verified: cross-checked the design against the current architecture, backend workflow, Maven JaCoCo setup, and frontend test gap; documentation whitespace and `git diff --check` passed. No Sonar analysis or GitHub ruleset was run.
- Pending: configure SonarQube Cloud projects, implement CI scans and frontend tests, enable required checks, and collect remote run evidence.

## 2026-09-20 — Updated and automated GitHub Actions pins

- Changed: upgraded backend CI to immutable `actions/checkout` v7.0.1, `actions/setup-java` v6.0.1, and `actions/upload-artifact` v7.0.1 SHAs; pinned both jobs to Ubuntu 24.04; disabled persisted checkout credentials; added weekly Dependabot updates for GitHub Actions.
- Verified: both GitHub YAML files parsed successfully; every workflow `uses:` reference passed the 40-character SHA check; `./scripts/verify.sh` passed all four Maven builds and the frontend production build when run with the local-network/Docker permissions required by WireMock and Testcontainers; GitHub-hosted `Backend CI` run 35504921337 passed all four module jobs, report uploads, and the aggregate test gate.
- Pending: none.

## 2026-09-20 — Expanded backend testing handoff

- Changed: rewrote `docs/BACKEND-TESTING-PLAN.md` as a detailed phase record covering the original baseline, decisions, Maven lifecycle, API/JWT contracts, per-module test matrix, implementation mapping, CI behavior, acceptance evidence, explicit gaps, and ordered follow-up work.
- Verified: cross-checked every listed test and status against the current test sources/POMs; `git diff --check` passed.
- Pending: implement the security/HTTP and gateway-resilience cases marked `Pending` in sections 6 and 12 of the plan.

## 2026-09-20 — Implemented backend production test phase

- Changed: added the backend testing plan; standardized validation/status/error responses; hardened JWT and ownership handling; added the unique-email Flyway migration; added JUnit/Mockito unit tests, MySQL 8.4 Testcontainers HTTP/security tests, Flyway upgrade tests, WireMock gateway route tests, Surefire/Failsafe/JaCoCo gates, and the pinned GitHub Actions backend matrix gate; extended smoke coverage for malformed JWT and cross-user deletion.
- Verified: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin:$PATH ./scripts/verify.sh` passed 31 Java tests across four independent Maven builds plus the frontend production build; auth/post/comment JaCoCo line coverage was 92.26%/93.28%/92.54% and branch coverage was 70.00%/72.22%/72.22%; `docker compose up --build -d` completed with all six containers healthy; `./scripts/smoke-test.sh` passed same-origin auth/post/comment flows, malformed JWT 401, cross-user 403, and owner cleanup.
- Pending: frontend Jest/component and Playwright E2E tests; frontend dependency audit/CRA migration; broader security scanning and trusted image release stages.

## 2026-09-20 — Added implementation roadmap

- Changed: added `docs/NEXT-STEPS.md` with the reviewed project status, milestone gaps, ordered implementation phases, acceptance evidence, and immediate checklist.
- Verified: documentation structure reviewed and `git diff --check` passed.
- Pending: begin Phase 1 by adding backend security/ownership tests, MySQL integration tests, and frontend component tests.

## 2026-09-18 — Runtime baseline aligned with source audit

- Changed: replaced Eureka/Config Server runtime dependencies with explicit service URLs; corrected three gateway routes and same-origin frontend API calls; standardized backend container ports; externalized DB/JWT settings; added Actuator/Prometheus endpoints and graceful shutdown.
- Changed: restored Maven Wrapper metadata; removed unused cross-service executable-JAR dependencies; added isolated H2 context tests; made Flyway migrations database-scoped.
- Changed: invalid JWTs now return 401; post/comment authors come from authenticated JWT identity; delete operations enforce ownership; signup checks the submitted email.
- Changed: added non-root multi-stage images, MySQL/bootstrap Compose stack, independent database users, local verification/smoke scripts, and agent handoff instructions.
- Removed: obsolete `config-server` and `discovery-server` modules plus generated/cache directories after confirming the target runtime contains five modules.
- Fixed during runtime verification: configured the Nginx document root so the SPA no longer loops to HTTP 500; made invalid-JWT responses return JSON HTTP 401 without an error redispatch; disabled Watchman in the repeatable frontend test command.
- Verified: `scripts/verify.sh` completed all four Maven builds/tests plus frontend test/build; Docker built all five application images; MySQL and all five application containers are healthy; `scripts/smoke-test.sh` passed signup, login, post, and comment flows through `http://localhost:3000`; malformed JWT returned HTTP 401.
- Pending: the React/CRA dependency tree reports 71 audit findings and has no component tests; frontend lint and Spring Security DSL deprecation warnings remain. Kubernetes/Helm, CI, Terraform/Ansible, GitOps, and the wider observability stack remain for later architecture milestones.
