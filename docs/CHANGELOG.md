# Source remediation history

This is the concise handoff log for implementation work. The architecture source of truth remains `Kien-truc-DevOps-BlogApp copy.md`.

## 2026-09-20 — Kubernetes lab command notes

- Changed: added `docs/Kubernetes-Labs.md` with Lab A cluster checks, Lab B Deployment/self-healing commands, Lab C Service/ClusterIP/port-forward commands, Lab D scaling commands, Lab E rollout failure/rollback commands, Lab F ConfigMap/Secret commands, Lab G PVC/persistent-storage commands, cleanup steps, the correct manifest structures, and explanations for the two YAML indentation errors encountered during practice.
- Verified: documentation-only change; the file records the local `kubectl` workflow but does not claim a cluster run that has not been reported.
- Pending: confirm Lab C through Lab G commands on the active cluster, then continue with Lab H cloud capstone and the remaining Kubernetes work.

## 2026-09-18 — Runtime baseline aligned with source audit

- Changed: replaced Eureka/Config Server runtime dependencies with explicit service URLs; corrected three gateway routes and same-origin frontend API calls; standardized backend container ports; externalized DB/JWT settings; added Actuator/Prometheus endpoints and graceful shutdown.
- Changed: restored Maven Wrapper metadata; removed unused cross-service executable-JAR dependencies; added isolated H2 context tests; made Flyway migrations database-scoped.
- Changed: invalid JWTs now return 401; post/comment authors come from authenticated JWT identity; delete operations enforce ownership; signup checks the submitted email.
- Changed: added non-root multi-stage images, MySQL/bootstrap Compose stack, independent database users, local verification/smoke scripts, and agent handoff instructions.
- Removed: obsolete `config-server` and `discovery-server` modules plus generated/cache directories after confirming the target runtime contains five modules.
- Fixed during runtime verification: configured the Nginx document root so the SPA no longer loops to HTTP 500; made invalid-JWT responses return JSON HTTP 401 without an error redispatch; disabled Watchman in the repeatable frontend test command.
- Verified: `scripts/verify.sh` completed all four Maven builds/tests plus frontend test/build; Docker built all five application images; MySQL and all five application containers are healthy; `scripts/smoke-test.sh` passed signup, login, post, and comment flows through `http://localhost:3000`; malformed JWT returned HTTP 401.
- Pending: the React/CRA dependency tree reports 71 audit findings and has no component tests; frontend lint and Spring Security DSL deprecation warnings remain. Kubernetes/Helm, CI, Terraform/Ansible, GitOps, and the wider observability stack remain for later architecture milestones.
