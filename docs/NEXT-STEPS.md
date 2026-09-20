# BlogApp — Hiện trạng và kế hoạch tiếp theo

Cập nhật lần cuối: 2026-09-20

Đây là tài liệu bàn giao ngắn để tiếp tục triển khai project. Nguồn kiến trúc chính vẫn là `Kien-truc-DevOps-BlogApp copy.md`, đặc biệt các mục 4, 6 và 17. Dùng `CHANGELOG.md` để kiểm tra những công việc đã thực sự hoàn thành và có bằng chứng chạy.

Kế hoạch triển khai chi tiết cho phase hiện tại nằm tại `BACKEND-TESTING-PLAN.md`.

## 1. Baseline hiện tại

Ứng dụng hiện có năm module runtime:

- `blog-client`
- `api-gateway-server`
- `userauthservice`
- `postservice`
- `commentservice`

Eureka và Spring Cloud Config đã được loại bỏ. Service discovery dùng DNS/URL cấu hình qua environment. Trình duyệt chỉ gọi API cùng origin qua `/api`.

Baseline Docker Compose local đang hoạt động:

- MySQL và cả năm container ứng dụng đều báo healthy.
- Chỉ `blog-client` được publish tại `http://localhost:3000`.
- Các image dùng multi-stage build và runtime non-root.
- Cấu hình database và JWT được truyền bằng environment variables.
- Auth, post và comment có database cùng application user riêng.
- Gateway có ba route riêng cho auth, post và comment.
- JWT không hợp lệ trả HTTP 401.
- Tác giả post/comment lấy từ JWT đã xác thực, không tin giá trị client gửi lên.
- Xóa post/comment kiểm tra quyền sở hữu phía server.
- Smoke flow signup, login, post và comment đã từng chạy thành công qua public frontend origin.

`scripts/verify.sh` hiện build thành công bốn module Maven và frontend production build. Backend quality gate của phase hiện tại đã được triển khai:

- Auth, post và comment có unit test, HTTP/security integration test và MySQL 8.4 Testcontainers test.
- Auth có Flyway test từ database trống và từ V1 lên latest.
- Gateway có WireMock route contract test cho ba nhóm API.
- Maven Surefire/Failsafe và JaCoCo chặn build; ngưỡng business service là 80% line và 70% branch.
- GitHub Actions chạy matrix `clean verify` cho bốn module và gom kết quả vào required check `backend-test-gate`.
- Frontend chưa có test và đang pass nhờ `--passWithNoTests`.
- Dependency tree frontend đang báo 71 audit findings.
- Frontend ESLint và Spring Security vẫn có cảnh báo deprecated.
- Gateway chưa expose Prometheus metrics như ba business service.

## 2. Nhánh học Kubernetes

Nhánh `feature/kubernetes-labs`, commit `fd9c0b5`, hiện chứa:

- Tài liệu Kubernetes Lab A đến Lab G.
- Bài Deployment và self-healing.
- ClusterIP Service và port-forward.
- Scale Deployment.
- Rollout lỗi và rollback.
- ConfigMap và Secret.
- PVC và persistent storage.

Các manifest này chủ yếu dùng Nginx để học Kubernetes, chưa phải deployment Kubernetes của BlogApp.

Trước khi merge nhánh:

1. Chạy và xác nhận Lab C đến Lab G trên cluster local đang dùng.
2. Ghi kết quả thật và các chỉnh sửa cần thiết vào tài liệu lab.
3. Chạy client-side validation cho mọi manifest được commit.
4. Ghi bằng chứng kiểm chứng vào `CHANGELOG.md`.
5. Chỉ merge vào `main` khi lệnh trong tài liệu khớp với hành vi quan sát được.

## 3. Trạng thái các mốc

| Mốc | Trạng thái | Phần còn thiếu |
| --- | --- | --- |
| 1. Audit và sửa ứng dụng | Backend quality baseline đạt | Frontend component/E2E test và dependency cleanup |
| 2. Docker và cấu hình ngoài | Đạt baseline local | Rà lại hardening khi đưa sang Kubernetes |
| 3. CI đầy đủ | Backend test gate đã có | Frontend gate, security scan, image build/sign/SBOM và release flow |
| 4. AWS dev bằng Terraform | Chưa làm | State, VPC, compute, LB, RDS, IAM và inventory |
| 5. kubeadm, platform dev và ASG | Chưa làm | Ansible, cluster bootstrap, storage và node lifecycle |
| 6. GitOps và observability dev | Chưa làm | Helm, Argo CD, Rollouts, Prometheus, Grafana, Loki và Tempo |
| 7. Staging tương đương prod | Chưa làm | Ba AZ, load, migration, rollback, restore và scaling evidence |
| 8. Prod và promotion | Chưa làm | Approval có audit và blue-green cùng digest |
| 9. Game day và bảo vệ | Chưa làm | Failure exercises, restore evidence và báo cáo SLO |

## 4. Kế hoạch triển khai

### Phase 1 — Hoàn thiện application quality baseline

Đây là ưu tiên ngay tiếp theo. Không coi context-load tests hiện tại là đủ bằng chứng nghiệm thu.

#### Backend tests — hoàn thành 2026-09-20

- [x] Thêm unit test cho tạo post, tạo comment và kiểm tra ownership.
- [x] Test JWT hợp lệ, malformed và hết hạn.
- [x] Xác nhận lỗi xác thực trả 401 thay vì 500.
- [x] Xác nhận user không phải chủ sở hữu nhận 403 khi xóa post/comment của người khác.
- [x] Xác nhận trường tác giả do client gửi không thể ghi đè authenticated identity.
- [x] Test signup trùng username và trùng email.
- [x] Thêm integration test bằng Testcontainers với MySQL 8.4.
- [x] Test Flyway từ database trống.
- [x] Test nâng Flyway từ V1 lên latest và giữ dữ liệu.
- [x] Khóa HTTP contract cho ba business service và gateway route contract bằng WireMock.
- [x] Cấu hình Maven Failsafe cho `*IT`, Surefire cho `*Test` và JaCoCo gate.

#### Frontend tests và bảo trì

- Thêm component tests cho signup, login, tạo post, tạo comment và các control phụ thuộc quyền.
- Test việc frontend luôn gọi API qua same-origin `/api`.
- Bỏ `--passWithNoTests` sau khi đã có test thật.
- Sửa các ESLint warnings và tách lint thành lệnh có thể làm CI fail.
- Lập PR riêng để chuyển khỏi Create React App đã ngừng duy trì.
- Rà soát từng audit finding; không chạy mù quáng `npm audit fix --force`.

#### Runtime và observability cleanup

- Chuyển Spring Security sang DSL không deprecated.
- Thêm `micrometer-registry-prometheus` cho gateway.
- Chỉ expose `health`, `info` và `prometheus` trên management port nội bộ.
- Không đưa Actuator management endpoints ra public ingress.
- Pin JDK 17 trong CI dù máy phát triển có Java mới hơn.

#### Lệnh nghiệm thu Phase 1

```bash
./scripts/verify.sh
docker compose up --build -d
docker compose ps
./scripts/smoke-test.sh
```

Chạy thêm negative-path tests cho malformed JWT và hành vi xóa chéo giữa hai user. Ghi exact commands và kết quả vào `CHANGELOG.md`.

### Phase 2 — Hoàn tất Kubernetes labs và làm BlogApp local capstone

Sau khi xác minh và merge các lab hiện tại, triển khai chính BlogApp lên Kubernetes local.

Capstone tối thiểu phải có:

- Deployment cho năm runtime module.
- ClusterIP Service cho các ứng dụng nội bộ.
- Một public entry point duy nhất, vẫn giữ `/api` đi qua gateway.
- ConfigMap cho cấu hình thường.
- Secret cho JWT và database credentials; chỉ commit ví dụ an toàn.
- Startup, readiness và liveness probes.
- CPU/memory requests và limits.
- Non-root security context, tắt privilege escalation.
- MySQL có PVC chỉ dành cho môi trường học local.
- Chiến lược migration không giả định MySQL production chạy trong Kubernetes.
- Smoke test qua public origin duy nhất.
- Test restart pod để chứng minh Service DNS vẫn ổn định.
- Test thay ConfigMap để chứng minh rollout có kiểm soát.

Phase này phải chứng minh migration gate của kiến trúc mục 4.3: Eureka và Config Server vẫn tắt, restart backend không làm hỏng routing, và thay đổi cấu hình tạo rollout đúng dự kiến.

### Phase 3 — Xây CI đầy đủ và trusted release artifacts

Tạo pull-request workflow với JDK 17 và Node version được pin.

Required PR checks:

- Maven `clean verify` độc lập cho bốn Java module.
- Frontend install, test, lint và production build.
- Testcontainers integration tests.
- JaCoCo coverage report.
- Gitleaks secret scanning.
- Trivy filesystem, dependency và config scanning.
- Manifest/Helm validation sau khi có chart.
- Một aggregate required check fail nếu job bắt buộc fail hoặc bị skip ngoài dự kiến.

Phải lưu bằng chứng:

- PR hợp lệ pass.
- Test cố tình làm lỗi chặn PR.
- Secret test bị phát hiện và chặn PR.
- CVE vượt policy chặn PR hoặc có exception ghi rõ owner, lý do và ngày hết hạn.

Trusted post-merge workflow phải:

1. Test lại merged commit.
2. Build năm image.
3. Scan image đã build.
4. Push GHCR với commit-SHA tag.
5. Lấy digest thật từ registry.
6. Tạo CycloneDX SBOM và provenance.
7. Ký digest bằng Cosign theo identity policy đã duyệt.
8. Sinh và validate release manifest.

Không promote `latest`, không rebuild giữa các môi trường, và không dùng tag ở nơi yêu cầu digest.

### Phase 4 — Helm và GitOps dev

- Tạo một BlogApp chart dùng chung hoặc shared service subchart.
- Tạo values riêng cho dev, staging và prod.
- Pin image bằng digest.
- Thêm Services, probes, resources, security contexts, PDB và topology spread.
- Thêm default-deny NetworkPolicy cùng các rule allow cần thiết.
- Tách database migration thành Job có kiểm soát; tắt auto-migration ở runtime Kubernetes đích.
- Cấu hình SOPS với key riêng cho từng môi trường.
- Cài Argo CD và Argo Rollouts ở dev.
- Chứng minh commit đổi digest tạo đúng rollout.
- Chứng minh preview fail-closed khi thiếu metrics hoặc smoke test thất bại.

Trước khi tạo repository hạ tầng/GitOps, xác nhận ranh giới ba repository ở kiến trúc mục 5:

- Source repo: ứng dụng, tests và CI.
- Infrastructure repo: Terraform, Ansible, versions và runbooks.
- GitOps repo: platform, chart, environment config, releases, policies, dashboards và alerts.

### Phase 5 — Provision AWS dev

Triển khai Terraform theo thứ tự:

1. Bootstrap S3 state, locking, encryption và CI trust.
2. VPC, subnets, routes, endpoints và security groups.
3. Control-plane EC2 cố định và worker ASG.
4. Internal Kubernetes API NLB.
5. Public ALB cùng DNS/TLS edge.
6. RDS MySQL và database networking.
7. Storage/backup buckets, KMS và workload identity foundations.
8. Worker lifecycle hooks và đường automation qua SSM.

Bằng chứng nghiệm thu gồm reviewed plan, apply output, state location, inventory và security-group checks. Không mở SSH hoặc Kubernetes API trực tiếp ra Internet.

### Phase 6 — Bootstrap kubeadm và dev platform

Dùng Ansible cho:

- OS baseline và time synchronization.
- Kernel modules và sysctl.
- containerd với cgroup driver đúng.
- Pin version kubeadm, kubelet và kubectl.
- Bootstrap control plane và worker có tính idempotent.
- Calico, CoreDNS, metrics-server, AWS cloud-controller-manager và EBS CSI.
- Đường quản trị và phục hồi qua SSM.

Acceptance tests:

- Tất cả node dự kiến trở thành Ready.
- Cross-node networking và DNS hoạt động.
- NetworkPolicy deny/allow được chứng minh.
- PVC provisioning hoạt động.
- Chạy Ansible lần hai không gây thay đổi phá hủy cluster.
- Worker do ASG tạo tự join và trở thành Ready.
- Scale-in drain node trước termination và tôn trọng PDB.

### Phase 7 — Observability dev

- Cài kube-prometheus-stack, Grafana, Loki, Tempo, Alloy, OTel Collector và external synthetic checks.
- Thêm ServiceMonitor cùng management Service riêng.
- Thêm OTel Java agent cho tracing, không tạo đường metrics trùng Micrometer.
- Xuất structured logs có service, environment, release, trace ID và span ID.
- Tạo dashboard cho service, ingress, JVM, database, Kubernetes, control plane, GitOps, release và telemetry.
- Tạo actionable alerts có owner, runbook và dashboard link.

Bằng chứng nghiệm thu:

- Theo một request từ gateway tới backend/JDBC trace.
- Từ trace mở được structured logs tương ứng.
- Hiển thị RPS, error rate, latency, JVM, DB pool và release marker.
- Lost scrape target và failed synthetic journey tạo alert.

### Phase 8 — Staging tương đương prod

- Dùng ba Availability Zones và ba control-plane nodes.
- Promote đúng digest đã qua dev.
- Test expand/migrate/contract migration với dữ liệu đại diện.
- Chạy load profile ban đầu 10 → 30 → 50 RPS và lưu đầy đủ test context.
- Test blue-green preview, promotion, abort và Git revert.
- Test HPA → pending pod → Cluster Autoscaler → ASG → node join → pod Ready.
- Test max-capacity và bootstrap-failure paths.
- Dừng một worker và đo phục hồi bằng external synthetic check.
- Restore RDS snapshot/PITR vào database mới và kiểm tra dữ liệu ứng dụng.

Không gọi staging là production-like nếu topology và bằng chứng lỗi thực tế chưa đạt.

### Phase 9 — Prod promotion và game day

- Chỉ promote digest đã vượt staging.
- Yêu cầu production GitOps review có audit.
- Chạy preview smoke và metric analysis trước promotion.
- Giữ ReplicaSet trước đủ lâu cho rollback đã đo.
- Revert desired state trong Git sau abort để Argo CD không đưa bản lỗi trở lại.
- Chứng minh credential dev không deploy được prod.
- Chạy controlled failure exercises cho bad release, lost worker, backup restore và alert delivery.
- Ghi RTO/RPO và SLO đo được; không báo mục tiêu thiết kế như kết quả đã đạt.

## 5. Bằng chứng cần lưu cho mỗi phase

Tùy phase, lưu các artifact sau:

- Source commit SHA.
- Tool/platform versions đã pin.
- Exact verification commands và kết quả.
- Test, coverage, lint và scan reports.
- Terraform plan đã review.
- Image digests, SBOM, provenance và signatures.
- GitOps promotion pull requests.
- Migration, load, rollback và restore reports.
- Dashboard và alert-rule exports.
- Bằng chứng alert firing/resolved.
- Số liệu deployment frequency, lead time, failure rate và recovery time.

Mỗi batch có ý nghĩa phải thêm một entry factual vào `docs/CHANGELOG.md`. Không đánh dấu test, deployment, môi trường hoặc acceptance criterion là hoàn thành nếu chưa có bằng chứng từ lần chạy thật.

## 6. Checklist ngay tiếp theo

- [ ] Thêm backend security và ownership tests.
- [ ] Thêm MySQL Testcontainers và Flyway integration tests.
- [ ] Thêm frontend component tests và bỏ `--passWithNoTests`.
- [ ] Sửa frontend lint và Spring Security deprecation warnings.
- [ ] Thêm Prometheus support cho gateway.
- [ ] Chạy lại full local verification và negative security tests.
- [ ] Xác minh Kubernetes Lab C–G trên local cluster.
- [ ] Merge `feature/kubernetes-labs` sau khi đã ghi bằng chứng.
- [ ] Xây BlogApp local Kubernetes capstone.
- [ ] Triển khai complete PR CI gate.
