# Trivy trong CI BlogApp

**Trạng thái 2026-09-25:** quét source/config được tích hợp vào CI; image scan chỉ build và quan sát trên `main` hoặc `workflow_dispatch`. Sonar backend đã được tích hợp từ main qua PR #4. Chưa có GHCR push, release manifest, SARIF upload hoặc required rule cho Trivy. Không mô tả đây là release pipeline production đã hoàn tất.

## Phạm vi và luồng

| Thời điểm | Mục tiêu | Kết quả |
| --- | --- | --- |
| Mọi PR và push `main` | `trivy fs --scanners vuln` đọc bốn Maven POM và frontend lockfile; `trivy config` đọc năm Dockerfile và các loại IaC được hỗ trợ sau này | JSON report, summary, check `Trivy source gate` |
| Push `main` hoặc chạy tay, sau `Backend test gate` | Build năm image local `linux/amd64`, quét OS và library của đúng image ID, tạo CycloneDX SBOM | Artifact `trivy-images-<run-id>`; chưa push registry |

`docker compose config --quiet` xác thực cấu trúc Compose bằng giá trị CI giả. Các lệnh Trivy bỏ qua `target`, `node_modules` và `.cache` để không lấy Dockerfile/dependency sinh ra trong bản build local làm source của dự án. Trivy v0.74.0 phát hiện năm Dockerfile nhưng **không phát hiện `compose.yaml`** trong baseline; không coi bước `trivy config` là kiểm toán bảo mật Compose. Khi thêm Helm/Terraform, xác nhận đường dẫn đó thật sự xuất hiện trong Trivy JSON trước khi coi là đã được quét.

Trivy v0.74.0 và `aquasecurity/setup-trivy` v0.3.1 được pin trong workflow bằng version/full commit SHA. DB cache tách theo Git ref để PR không ghi đè cache `main`; scanner phải tự cập nhật DB/check bundle khi cần. Lỗi tải DB, report thiếu, mục tiêu bắt buộc bị bỏ qua hoặc scan timeout đều làm job thất bại. Quét PR dùng `pull_request`, chỉ có `contents: read`; không dùng `pull_request_target`, registry token hay AWS credential.

## Chính sách gate và ngoại lệ

Job source chờ `Backend test gate`, khôi phục Maven cache dùng chung key với job gateway, rồi chạy `./mvnw -B -ntp dependency:go-offline -DskipTests` cho cả bốn backend. Bước này chuẩn bị POM/dependency trong local Maven repository trước khi Trivy phân tích, tránh tải trực tiếp hàng loạt từ Maven Central (run đầu đã gặp HTTP 429). Lỗi resolve dependency vẫn làm job thất bại; không bỏ qua dependency chưa phân tích được.

Một số POM Trivy cần vẫn không nằm trong cache sau `go-offline`. `security/trivy/trivy.yaml` trỏ các lần lấy metadata Maven Central còn thiếu qua [mirror công khai của Google](https://storage-download.googleapis.com/maven-central/index.html), dùng [cơ chế mirror của Trivy](https://trivy.dev/docs/v0.74/guide/coverage/language/java/#config-file-mirrors). Cấu hình chỉ áp dụng cho scanner; database CVE không thay đổi, không bật `--offline-scan` vì cờ đó có thể bỏ sót dependency.

`scripts/trivy_policy.py` chỉ chặn lỗ hổng **HIGH/CRITICAL có bản vá** và lỗi cấu hình **HIGH/CRITICAL**. Finding thấp hơn hoặc chưa có bản vá vẫn ở báo cáo đầy đủ để triage. Job source chạy `--mode enforce`, nên thất bại nếu còn finding chưa được xử lý; **chưa thêm check này vào required ruleset**. Job image hiện chạy `--mode audit` để đo nợ image, in cảnh báo và không được dùng làm bằng chứng đủ điều kiện phát hành.

Ngoại lệ ở `security/trivy/exceptions.json` mặc định rỗng. Chỉ thêm một entry khi có owner, GitHub issue, lý do cụ thể, ngày hết hạn trong 30 ngày và định danh chính xác: stage + module + finding ID + package (lỗ hổng) hoặc target (cấu hình). Policy bác ngoại lệ hết hạn, trùng hoặc không còn khớp. Không tạo ngoại lệ hàng loạt cho nợ hiện có. Ví dụ cấu trúc, **không phải ngoại lệ đã được chấp nhận**:

```json
{
  "stage": "source",
  "module": "blog-client",
  "id": "CVE-EXAMPLE",
  "package": "example-lib",
  "owner": "@maintainer",
  "issue": "https://github.com/Vinh5905/blog_app_microservices/issues/123",
  "reason": "Temporary mitigation documented in the issue",
  "expires": "2026-10-01"
}
```

## Baseline đã đo

Chạy Trivy v0.74.0 ngày 2026-09-25 trên checkout local, DB mới tải. Source có **531 findings**, trong đó **244 HIGH/CRITICAL có bản vá**: gateway 38, frontend 92, auth 38, post 38, comment 38. Con số thay đổi theo DB và source SHA; artifact của từng run là nguồn chính xác. Bước config phát hiện một HIGH (`DS-0002`) do Dockerfile frontend không ghi `USER`; đã ghi rõ `USER 101` trên final stage dùng `nginx-unprivileged`, và lượt `trivy config` tiếp theo báo **0 HIGH/CRITICAL** trên đúng năm Dockerfile. Năm image local sau build/scan vẫn còn **186 HIGH/CRITICAL có bản vá**: frontend 38, gateway 37, auth 37, post 37, comment 37. Policy đã đối chiếu report với Docker image ID từng image; cả năm image cũng tạo được CycloneDX SBOM. Job [Trivy image baseline trên GitHub](https://github.com/Vinh5905/blog_app_microservices/actions/runs/36144854263/job/108103813307) cũng đã thành công trên commit 471a852: artifact chứa đủ năm report, năm CycloneDX SBOM và image ID khớp, với cùng 186 findings. Đây là image audit, chưa phải gate phát hành hoặc registry digest.

Nợ 244 finding cần PR nâng dependency có kiểm thử riêng: ưu tiên Spring Boot/Cloud, Netty/Jackson và frontend `react-scripts`/CRA. Không bật required `Trivy source gate` hoặc phát hành image chỉ vì scanner đã chạy. Sau khi remediation và các ngoại lệ hẹp hợp lệ làm check xanh trên PR thật, owner chọn đúng check/source trong GitHub ruleset `main` và kiểm tra một PR CVE đối chứng bị chặn.

## Chốt luồng release sau baseline

Khi Sonar và các gate nguồn đã hoạt động trên **đúng merged SHA**, đổi image policy sang `enforce`; mọi image cần pass trước khi cùng image local đó được push lên GHCR. Lấy registry digest của từng image rồi mới tạo release manifest, SBOM/provenance/signature theo kiến trúc mục 7.2. Không dùng một report của tag cũ cho digest mới. Bổ sung quét lại digest đang phát hành hằng ngày và upload SARIF từ push `main` sau khi quyền `security-events: write`/Code Scanning được xác nhận. Job baseline hiện tại không push và không có quyền `packages: write`.

**Nghiệm thu còn thiếu:** CI PR sạch xanh, PR có CVE hoặc cấu hình HIGH đỏ, ngoại lệ hết hạn đỏ, image lỗi chặn push, GHCR digest/SBOM/signature/release manifest đúng SHA, ruleset thật chặn merge. Chỉ cập nhật `docs/CHANGELOG.md` là hoàn tất khi có URL run và kết quả thực tế.

## Nguồn

- [Trivy Java coverage](https://trivy.dev/docs/latest/guide/coverage/language/java/) và [misconfiguration scanning](https://trivy.dev/docs/latest/scanner/misconfiguration/).
- [Trivy image targets](https://trivy.dev/docs/latest/guide/target/container_image/), [filtering](https://trivy.dev/docs/latest/configuration/filtering/) và [GitHub Action](https://github.com/aquasecurity/setup-trivy).
- [GitHub `pull_request_target` guidance](https://docs.github.com/en/actions/reference/security/securely-using-pull_request_target/) và [SARIF upload](https://docs.github.com/en/code-security/how-tos/scan-code-for-vulnerabilities/integrate-with-existing-tools/uploading-a-sarif-file-to-github).
