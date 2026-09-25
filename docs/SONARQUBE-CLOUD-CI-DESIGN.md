# SonarQube Cloud trong CI của BlogApp

**Trạng thái:** PR #2 là draft để kiểm chứng CI, đã chạy thành công bốn backend scan và Quality Gate trên GitHub; ruleset `main` đã bật hai required check tổng hợp. Chưa đưa workflow vào `main` và frontend vẫn ở giai đoạn baseline thủ công.

**Ngày:** 2026-09-25.

**Phạm vi:** repository nguồn hiện tại với năm module `blog-client`, `api-gateway-server`, `userauthservice`, `postservice`, `commentservice`. SonarQube Cloud kiểm tra mã nguồn trong CI; việc quét secret, dependency, image và IaC vẫn là các gate riêng của [kiến trúc tổng thể](Kien-truc-DevOps-BlogApp%20copy.md), mục 7.

## Cấu hình triển khai trong repository

- .github/workflows/backend-ci.yml chạy bốn Maven verify, lưu bytecode và JaCoCo XML theo module, rồi chạy bốn SonarScanner for Maven với các key bên dưới. Check Sonar quality gate chỉ pass khi cả bốn scan và Quality Gate đều pass.
- .github/workflows/frontend-sonar-baseline.yml chỉ chạy thủ công từ main. Scanner đọc blog-client/sonar-project.properties và chưa nhập coverage vì frontend chưa có LCOV. Workflow này chưa là required check.
- Trên GitHub repo, tạo Actions Secret SONAR_TOKEN bằng token Sonar có quyền Execute Analysis trên năm project. Tạo Actions Variable SONAR_ORGANIZATION bằng **organization key hiển thị trong Sonar UI**. Nếu project ở vùng US, tạo thêm variable SONAR_REGION=us; để trống ở vùng EU. Không đưa token vào variables hoặc committed files.

| Module | Sonar project key |
| --- | --- |
| userauthservice | vinh5905_blog_app_microservices_userauthservice |
| postservice | vinh5905_blog_app_microservices_postservice |
| commentservice | vinh5905_blog_app_microservices_commentservice |
| api-gateway-server | vinh5905_blog_app_microservices_api-gateway-server |
| blog-client | vinh5905_blog_app_microservices_blog-client |

Các key lấy từ ảnh cấu hình project do chủ repository cung cấp. [PR #2](https://github.com/Vinh5905/blog_app_microservices/pull/2) là draft để kiểm chứng CI; [GitHub Actions run 36094511394](https://github.com/Vinh5905/blog_app_microservices/actions/runs/36094511394) xác nhận bốn scan backend, bốn check SonarQube Cloud và hai job tổng hợp đều pass. [Ruleset cho `main`](https://github.com/Vinh5905/blog_app_microservices/settings/rules/23979594) đang Active, yêu cầu PR cùng `Sonar quality gate` và `Backend test gate` từ GitHub Actions. Khi có PR tích hợp được duyệt và merge, kiểm tra baseline trên `main` và chạy frontend baseline thủ công để xác nhận scope/coverage.

## 1. Quyết định và ranh giới

- Dùng GitHub Actions để chạy **CI-based analysis**. SonarQube Cloud không tự chạy Automatic Analysis cho cùng project, vì CI phải nạp coverage từ JaCoCo/Jest. Tạo năm Sonar project trong cùng GitHub monorepo, mỗi project có key, phạm vi phân tích và lịch sử Quality Gate riêng.
- Tài khoản SonarQube Cloud **Free** dùng Quality Profile và Quality Gate **Sonar way** mặc định. Gói này không tạo được custom Quality Gate; Sonar way hiện yêu cầu coverage code mới từ 80%, duplication code mới tối đa 3%, ratings và Security Hotspots theo điều kiện của Sonar. Không diễn giải thành “không có blocker mới” nếu gate thực tế không đặt điều kiện đó. Khi nhà cung cấp thay đổi gate, phải kiểm tra lại trước khi thay policy CI.
- Chỉ phân tích PR từ nhánh **trong cùng repository** nhắm vào `main`, và push vào `main`. PR từ fork không được cấp `SONAR_TOKEN` và không nằm trong luồng Sonar của thiết kế này. Không dùng `pull_request_target` để chạy mã PR với secret.
- Giai đoạn 1: Sonar Gate của **bốn Java module** là required; frontend chỉ nhận baseline analysis thủ công trên `main` và chưa là required. Giai đoạn 2: thêm test frontend sinh LCOV, sau đó đưa `blog-client` vào required gate. Không đánh dấu mốc CI đầy đủ ở mục 17 của kiến trúc chỉ vì Sonar đã pass; secret/CVE/frontend và các gate khác có tiêu chí riêng.
- Giữ `./mvnw clean verify` độc lập cho từng Java service. Sonar bổ sung kiểm tra trên code mới, không thay thế Surefire, Failsafe, JaCoCo hiện có. Gateway hiện có JaCoCo report nhưng chưa có percentage gate; Sonar way vẫn áp dụng lên code mới của gateway, nên phải bổ sung test khi thay đổi đáng kể thay vì nới exclusion.

## 2. Khởi tạo SonarQube Cloud

1. Kết nối GitHub account/organization chứa `Vinh5905/blog_app_microservices` với SonarQube Cloud và chỉ cấp quyền GitHub App cần cho repository này. Xác nhận account đang ở gói Free, vùng Sonar server, trạng thái public/private và hạn mức LOC còn lại. Theo hướng dẫn Sonar hiện tại, account mới tạo ở vùng EU; vùng US cần Enterprise. Free hỗ trợ tối đa 50.000 LOC private trong organization; nếu vượt hạn mức, dừng rollout và xử lý gói/tính khả thi, không làm scanner “pass” bằng cách loại bỏ mã nghiệp vụ.
2. Import repository theo hướng dẫn **monorepo** thành năm project, bound về cùng GitHub repository. Dùng key ổn định theo mẫu `<sonar-org-key>_blog_app_microservices_<module>`; lấy chính xác organization key, project key và server region từ UI khi tạo, không đoán từ GitHub username. Bảng ánh xạ phải được lưu trong tài liệu triển khai/biến cấu hình CI, không chứa token.

   | Sonar project | Thư mục phân tích | Coverage đầu vào |
   | --- | --- | --- |
   | `..._userauthservice` | `userauthservice/` | `target/site/jacoco/jacoco.xml` |
   | `..._postservice` | `postservice/` | `target/site/jacoco/jacoco.xml` |
   | `..._commentservice` | `commentservice/` | `target/site/jacoco/jacoco.xml` |
   | `..._api-gateway-server` | `api-gateway-server/` | `target/site/jacoco/jacoco.xml` |
   | `..._blog-client` | `blog-client/` | `coverage/lcov.info` sau khi có test |

3. Tắt **Automatic Analysis** trên cả năm project trước khi dùng scanner CI. Chọn Sonar way cho từng project. Ghi ngày baseline; đặt New Code Definition của `main` theo **Specific date** tại thời điểm baseline để code cũ hiện ra như technical debt nhưng không bất ngờ chặn rollout. Sonar yêu cầu Web API cho lựa chọn Specific date; lưu giá trị và cách thay đổi trong runbook. PR analysis luôn so sánh với target `main`. Chạy main analysis đủ lần để Quality Gate có trạng thái tính được trước khi bật branch rule; `Not computed` không được xem là pass.
4. Chỉ sau khi scan thực tế, kiểm tra mỗi project có đúng module, số LOC hợp lý, test code được nhận diện là test, JaCoCo XML được nhập và không có source từ module khác. Không loại controller, service, security filter hoặc error handler chỉ để tăng coverage.

## 3. Luồng CI đích

```text
PR nội bộ -> bốn Maven clean verify độc lập -> JaCoCo XML -> bốn Sonar analysis
            -> chờ bốn Quality Gate -> sonar-quality-gate -> GitHub ruleset -> merge
push main   -> kiểm thử/analysis lại đúng merged commit -> gate -> các bước release về sau
                                                 frontend baseline thủ công trên main (giai đoạn 1)
```

### 3.1 Backend

- Giữ matrix `verify` bốn module và `Backend test gate` trong `.github/workflows/backend-ci.yml`. Workflow chạy trên mọi PR nhắm `main` và mọi push `main`, không đặt path filter khiến required check biến mất. PR checkout merge ref mặc định để kiểm thử kết quả tích hợp; push checkout đúng merged SHA. Mỗi module tiếp tục chạy trên Ubuntu 24.04, Temurin 17, Testcontainers và `./mvnw -B -ntp clean verify`. Sau `verify`, kiểm tra `target/site/jacoco/jacoco.xml` tồn tại và có nội dung; upload riêng `target/classes`, `target/test-classes`, JaCoCo XML và test reports theo tên artifact chứa module. Artifact phải lấy từ **cùng workflow run**; thiếu file bắt buộc thì job fail. Không dùng `-DskipTests`, `continue-on-error` hoặc `if-no-files-found: ignore` cho dữ liệu Sonar bắt buộc.
- Thêm matrix `sonar` phụ thuộc `verify`: checkout đúng SHA với `fetch-depth: 0`, tải artifact của từng module về đúng đường dẫn `target/` trong workspace sạch, rồi gọi **chỉ goal SonarScanner for Maven** từ thư mục module, không chạy lại test. Dùng project key/organization/region đã xác nhận, plugin **pin phiên bản**, `sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml`, `sonar.qualitygate.wait=true` và thời gian chờ hữu hạn. Scanner phải nhận ra bytecode và XML vừa tải; một analysis thiếu coverage hoặc bytecode là lỗi cấu hình, không phải gate pass. Checkout đủ Git history để xác định code mới và blame chính xác.
- Giữ secret `SONAR_TOKEN` **chỉ ở bước scanner**; không đặt ở workflow/job level và không đưa token vào tham số dòng lệnh. Project key và organization key dùng repository variables hoặc cấu hình công khai; URL/region phải khớp nơi project được tạo. Pin mọi Action bằng full commit SHA đã kiểm tra như workflow hiện tại, rồi dùng Dependabot để rà soát cập nhật.
- Tạo check tổng hợp ổn định `Sonar quality gate` phụ thuộc matrix `sonar`, chạy với `if: always()` và chỉ pass khi cả bốn module thực sự có analysis thành công **và** Quality Gate `OK`. Nếu matrix fail, cancel, skip, report thiếu, token thiếu, Sonar không trả kết quả hoặc hết thời gian chờ, check tổng hợp fail. `Backend test gate` tiếp tục phụ thuộc riêng matrix `verify` và phản ánh riêng kết quả test.
- Chỉ required check tổng hợp `Sonar quality gate` sau khi đã có run thành công trên PR nội bộ và đã xác minh branch ruleset chọn đúng tên check cùng GitHub App/source. `Backend test gate` vẫn required. Workflow release trong tương lai phải phụ thuộc kết quả CI của **đúng merged SHA**, không lấy gate của PR commit hoặc một run cũ.

### 3.2 Frontend và hai giai đoạn kích hoạt

- Giai đoạn 1 tạo Sonar project frontend, scan `blog-client/src` trên `main` qua `workflow_dispatch` để ghi nhận technical debt, không gắn scan này vào PR hoặc push CI và không đưa frontend vào `Sonar quality gate` required. Ghi rõ trong tài liệu vận hành rằng frontend chưa có coverage gate; việc Sonar report frontend xanh không chứng minh test frontend đã chạy.
- Trước giai đoạn 2, thêm Jest/component tests có ý nghĩa cho luồng đăng nhập, bài viết, bình luận và lời gọi same-origin `/api`; bỏ `--passWithNoTests` khỏi CI chính thức. Chạy `npm ci`, test không watch với `--coverage` để tạo `coverage/lcov.info`, lint và production build. Dùng SonarScanner CLI/Action đã pin SHA với `projectBaseDir: blog-client`, chỉ rõ `sonar.sources=src`, test patterns và `sonar.javascript.lcov.reportPaths=coverage/lcov.info`. Kiểm tra source/test không bị đếm trùng.
- Khi một PR frontend tốt pass và một PR thiếu coverage bị chặn bởi Sonar, thêm frontend PR analysis vào cùng check `Sonar quality gate` và giữ tên check required không đổi. Không đặt frontend ở chế độ `continue-on-error` sau mốc này.

### 3.3 Coverage và scope

- JaCoCo XML phải được tạo **trước** Sonar analysis. So sánh tỷ lệ trong Sonar với JaCoCo trên cùng tập file. Ba service nghiệp vụ hiện có gate JaCoCo tổng thể 80% line/70% branch; chỉ định `sonar.coverage.exclusions` tối thiểu, đồng bộ với các exclusion bootstrap/data-holder hiện có trong POM và được review bằng PR. `sonar.exclusions` chỉ dùng cho generated/cache/build files, không dùng để ẩn mã cần phân tích.
- Sonar way kiểm tra **new code** trên PR, còn JaCoCo hiện kiểm tra tỷ lệ tổng thể của ba service; hai gate có thể cho kết quả khác nhau mà không mâu thuẫn. Sonar mặc định chưa áp điều kiện coverage/duplication khi dưới 20 dòng code mới; test nghiệm thu coverage phải sửa đủ dòng để kiểm chứng gate. Không tuyên bố mọi PR nhỏ đều bị chặn bởi ngưỡng 80%.
- Khi thêm migration, test hoặc thư mục mới, rà lại scope và các đường dẫn report. Nếu Sonar báo coverage `0%` dù JaCoCo có dữ liệu, coi là lỗi nhập report/scope và chặn merge cho đến khi sửa.

## 4. Secret, PR đặc biệt và lỗi dịch vụ

- Tạo token chỉ có quyền **Execute Analysis** trên năm project, thuộc danh tính CI chuyên dụng nếu giới hạn thành viên Free cho phép; tránh dùng token quản trị/cá nhân có quyền rộng. Lưu trong GitHub Actions Secret `SONAR_TOKEN`, đặt lịch xoay và thu hồi khi nghi lộ. Không commit `.env`, token, log debug scanner hoặc artifact chứa credential. Job mặc định `contents: read`; không cấp `packages: write`, `id-token: write` hay AWS credential cho Sonar.
- PR từ fork chạy các test không cần secret nếu chính sách GitHub cho phép, nhưng Sonar check phải fail/không đủ điều kiện merge. Người duy trì muốn nhận thay đổi phải review rồi đưa nó lên nhánh nội bộ để chạy gate; không dùng `pull_request_target` với checkout và thực thi code fork. Đây là giới hạn đã chọn, không phải bằng chứng fork PR đã được Sonar phân tích.
- Dependabot PR cũng không có Actions Secret thông thường. Vì repository hiện có Dependabot cập nhật GitHub Actions, người duy trì review thay đổi rồi đưa commit lên nhánh nội bộ để chạy required gate; PR bot gốc không được merge trực tiếp. Nếu sau này muốn merge PR bot trực tiếp, phải review phương án **Dependabot Secret** với token phân tích quyền hẹp và kiểm chứng trên PR bot thật trước khi đổi quy trình. Không bypass required check và không coi job Sonar bị skip là pass.
- Khi Sonar Cloud không truy cập được, token bị thu hồi, vượt quota hoặc gate `Not computed`, fail closed. Cho phép rerun cùng SHA sau khi dịch vụ phục hồi; không tự chuyển sang `continue-on-error` hay dùng report cũ. Ghi lỗi, Sonar analysis URL, project key, GitHub run ID, source SHA và quyết định xử lý trong evidence.

## 5. Thứ tự triển khai và nghiệm thu

1. Xác nhận gói/quota/region, import năm project và khóa Automatic Analysis. Chạy baseline `main`; kiểm tra mapping, scope, JaCoCo và trạng thái gate thực tế.
2. Thêm backend Sonar scans và check tổng hợp vào CI theo mục 3.1. Chạy PR nội bộ tốt, PR có lỗi Sonar, PR làm coverage code mới dưới 80% với ít nhất 20 dòng mới; xác nhận check tổng hợp phản ánh đúng từng tình huống. Chạy thêm case report thiếu, token thiếu và một matrix job fail/skip bằng nhánh thử nghiệm.
3. Bật `Sonar quality gate` cùng `Backend test gate` trong GitHub ruleset bảo vệ `main` sau khi tên/source check đã xuất hiện từ run thật. Xác minh bằng PR rằng nút merge bị chặn khi Sonar đỏ và được mở khi mọi check required xanh. Ghi ảnh/chứng cứ ruleset và URL run; không đánh dấu branch protection đã bật chỉ từ YAML.
4. Giữ frontend ở trạng thái quan sát. Sau khi test/LCOV/lint/build đạt, chạy hai PR frontend đối chứng, rồi thêm frontend vào required gate như mục 3.2. Xác minh push `main` sau merge phân tích đúng SHA.
5. Khi có pipeline release, chỉ cho bước image/release manifest nhận merged SHA đã qua test, Sonar và các gate security riêng. Lưu cùng release evidence các URL Sonar analysis và Quality Gate status của đúng SHA; Sonar không thay thế Gitleaks, Trivy hay kiểm tra image.

**Bằng chứng hoàn tất Sonar rollout:** năm project đúng phạm vi; bốn backend PR gates và sau đó frontend gate chạy thực tế; ruleset chặn PR lỗi; report coverage được nhập; run `main` của merged SHA pass; failure cases không bị skip xanh. Đến lúc đó mới cập nhật trạng thái trong `docs/CHANGELOG.md` bằng lệnh, run URL và kết quả có thật.

## Nguồn tham chiếu

- [SonarQube Cloud subscription plans](https://docs.sonarsource.com/sonarqube-cloud/administering-sonarcloud/managing-subscription/subscription-plans) — giới hạn Free, PR analysis và custom gates.
- [GitHub Actions CI-based analysis](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/ci-based-analysis/github-actions-for-sonarcloud) — monorepo, scanner, Quality Gate wait và fork PR.
- [Quality Gates](https://docs.sonarsource.com/sonarqube-cloud/standards/managing-quality-gates/introduction-to-quality-gates) — Sonar way, new code và ngưỡng 20 dòng.
- [Java coverage](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/java-test-coverage) và [JavaScript/TypeScript coverage](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/javascript-typescript-test-coverage) — JaCoCo XML và LCOV.
- [GitHub secrets](https://docs.github.com/en/code-security/reference/secret-security/secret-types) — giới hạn secret với fork và Dependabot.
