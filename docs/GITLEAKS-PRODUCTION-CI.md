# Gitleaks production CI cho BlogApp

**Ngày triển khai:** 2026-09-30

**Gate ổn định:** `Gitleaks secret gate`

**Scanner:** Gitleaks `8.30.1`, Linux x64 archive SHA-256 `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb`

## 1. Phạm vi và quyết định

Gate chạy trên pull request vào `main`, push `main` và `workflow_dispatch`. Job chỉ có quyền `contents: read`, checkout với `fetch-depth: 0` và không giữ GitHub credential trong checkout.

Mỗi lần chạy có hai scan độc lập:

1. `gitleaks git --log-opts=--all` quét toàn bộ lịch sử Git còn reachable, gồm các commit của PR.
2. `gitleaks dir` quét snapshot của toàn bộ file tracked tại `HEAD` được tạo bằng `git archive`. Scan này đọc archive lồng nhau tối đa hai cấp và không đọc `.git`, cache hoặc file local không tracked.

Repo nhỏ và baseline lịch sử ngày 2026-09-30 sạch, nên không duy trì file baseline để bỏ qua phát hiện cũ. Cấu hình [Gitleaks](../security/gitleaks/gitleaks.toml) kế thừa toàn bộ default rules của binary đã pin và không có disabled rule, allowlist hoặc exception. CI truyền `--ignore-gitleaks-allow` và một ignore file rỗng, do đó comment inline và `.gitleaksignore` không thể âm thầm bỏ qua finding.

## 2. Tính lặp lại và chuỗi cung ứng

Workflow tải đúng archive release chính thức qua HTTPS, so khớp SHA-256 đã review rồi mới giải nén. Version scanner thực tế phải bằng version đã pin. Thay version hoặc checksum phải đi qua PR và chạy lại baseline; Dependabot không tự thay binary này.

Không truyền `SONAR_TOKEN`, registry credential, AWS credential hoặc secret ứng dụng vào job. Gitleaks chỉ cần source và Git history.

## 3. Fail-closed và bằng chứng

Gate thất bại khi xảy ra một trong các trường hợp:

- History scan hoặc current-tree scan tìm thấy ít nhất một secret.
- Scanner timeout, lỗi thực thi hoặc không tạo report JSON.
- Report có finding chưa được redact hoàn toàn.
- Cấu hình tắt default rules, thêm allowlist hoặc repository chứa `.gitleaksignore` tracked.
- Evidence không thuộc đúng `GITHUB_SHA` hoặc artifact ID bị thiếu/trùng trong release job.

Hai report JSON, version, source SHA và summary được upload trong artifact `gitleaks-<run-id>-<attempt>`, retention 14 ngày. Mọi finding trong report phải có trường `Secret` bằng `REDACTED`; summary chỉ giữ số lượng, rule và đường dẫn file. Nếu report chưa redact hoặc JSON hỏng, bước xác thực xóa các report trước khi artifact được upload và chỉ lưu summary lỗi an toàn.

Trên push `main`, `Publish verified images` phụ thuộc trực tiếp vào gate này. Trước lần push GHCR đầu tiên, release job tải artifact bằng đúng producer artifact ID, xác minh lại source SHA, status, hai report rỗng và version scanner. `gitleaks-summary.json` cùng hash của nó được lưu trong release artifact 90 ngày.

## 4. Xử lý khi phát hiện secret

Không chỉ xóa chuỗi rồi rerun. Người duy trì phải:

1. Thu hồi hoặc xoay credential ở hệ thống phát hành nó.
2. Chuyển giá trị sang GitHub Actions Secret hoặc secret store phù hợp.
3. Xóa secret khỏi toàn bộ commit của PR trước khi merge; cân nhắc rewrite lịch sử nếu nó đã vào nhánh chia sẻ.
4. Rerun cùng source SHA mới và lưu bằng chứng xử lý. Không thêm suppression để làm gate xanh.

Nếu finding là false positive, sửa fixture thành giá trị giả rõ ràng hoặc điều chỉnh cách biểu diễn dữ liệu. Mọi nhu cầu ngoại lệ trong tương lai phải có owner, lý do, phạm vi hẹp và ngày hết hạn trong một thay đổi policy riêng; cấu hình hiện tại cố ý chưa hỗ trợ ngoại lệ.

## 5. Nghiệm thu GitHub

Sau khi push branch triển khai:

1. Xác nhận một PR sạch có `Gitleaks secret gate` xanh và artifact đủ năm file evidence.
2. Trên branch thử nghiệm riêng, thêm một credential **giả** khớp rule mặc định, xác nhận gate đỏ và report đã redact, rồi xóa/rewrite commit thử nghiệm.
3. Thêm đúng check `Gitleaks secret gate` từ GitHub Actions vào ruleset bảo vệ `main` và bật yêu cầu branch cập nhật trước merge.
4. Xác nhận PR secret không thể merge và push `main` sạch vẫn tạo release evidence có Gitleaks status `pass`.

Chỉ đánh dấu nghiệm thu remote hoàn tất sau khi có URL PR/run cho cả case pass và case fail. Baseline local hoặc YAML đúng cú pháp chưa chứng minh ruleset thực sự chặn merge.
