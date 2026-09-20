# Phase Backend Testing — Kế hoạch, implementation và handoff

## 0. Trạng thái tài liệu

- Phase: application quality baseline cho backend.
- Phạm vi: `userauthservice`, `postservice`, `commentservice`, `api-gateway-server` và CI gate tương ứng.
- Trạng thái: baseline đã chạy được và quality gate hiện tại đã pass; một số case trong kế hoạch ban đầu vẫn còn thiếu và được liệt kê rõ tại mục 12.
- Lần kiểm chứng gần nhất: 2026-09-20, bằng JDK 17 và MySQL 8.4 Testcontainers.
- Nguồn kiến trúc: mục 4, 6 và 17 của `Kien-truc-DevOps-BlogApp copy.md`.
- Nhật ký bằng chứng ngắn: `CHANGELOG.md`.

Tài liệu này vừa là kế hoạch gốc của phase, vừa là implementation handoff. Agent tiếp theo không được suy ra rằng mọi dòng trong kế hoạch ban đầu đều đã hoàn thành; phải dùng các cột trạng thái và mục “Khoảng trống còn lại”.

## 1. Vì sao phase này được tạo

Trước phase này, bốn module Java chỉ có context-load tests dựa trên H2. Chúng chứng minh Spring có thể khởi tạo trong một cấu hình test đơn giản, nhưng không chứng minh được các yêu cầu production quan trọng:

- Nghiệp vụ tạo/xóa post và comment.
- Quyền sở hữu dữ liệu.
- Username phải lấy từ JWT đã xác thực thay vì request body.
- JWT malformed/expired phải trả 401, không trở thành 500.
- Status code và error body nhất quán giữa các service.
- Signup trùng username/email.
- MySQL 8.4 và Flyway migration thực sự tương thích.
- Route gateway giữ nguyên path/query/body/status và đi đúng upstream.
- Mỗi service có thể build/test độc lập bằng Maven Wrapper.
- Pull request bị chặn khi unit test, integration test hoặc coverage fail.

H2 tests cũ vì vậy bị loại bỏ. MySQL behavior chỉ được coi là có bằng chứng khi chạy trên MySQL 8.4 thật qua Testcontainers.

## 2. Phạm vi và quyết định đã chốt trước khi implement

### 2.1 Trong phạm vi phase

- Unit tests cho auth, post và comment.
- HTTP/security integration tests cho ba business services.
- MySQL 8.4 Testcontainers và Flyway tests.
- Gateway route contract tests bằng WireMock.
- Maven Surefire/Failsafe separation.
- JaCoCo coverage report và build gate.
- GitHub Actions matrix chạy bốn Maven module trên JDK 17.
- Chuẩn hóa tối thiểu production code để API có thể test theo contract ổn định.
- Docker Compose smoke test cho happy path và các negative security paths chính.

### 2.2 Ngoài phạm vi phase

- Frontend Jest/component tests.
- Playwright browser E2E.
- Spring Cloud Contract hoặc consumer-driven contract broker.
- Secret scanning, SAST, dependency policy, image scanning, SBOM, signing và provenance.
- Kubernetes/Helm/GitOps tests.
- Chuyển JWT sang asymmetric signing hoặc triển khai token revocation.

Frontend chỉ được đổi tối thiểu để đọc trường `detail` của Problem Details; frontend test automation thuộc phase sau.

### 2.3 Quyết định kỹ thuật

| Chủ đề | Quyết định |
| --- | --- |
| Java | CI và acceptance dùng JDK 17 |
| Database integration | MySQL `8.4.0` qua Testcontainers, không dùng H2 làm bằng chứng |
| Unit framework | JUnit 5, Mockito, AssertJ |
| HTTP integration | `@SpringBootTest(RANDOM_PORT)` và `TestRestTemplate` |
| Gateway integration | `WebTestClient` và ba WireMock upstream server |
| Migration | Flyway từ database trống và auth V1 lên latest |
| Test naming | `*Test` là unit/context test; `*IT` là integration test |
| Error contract | RFC 9457/Spring `ProblemDetail` với application-specific `code` |
| Coverage | Auth/post/comment: tối thiểu 80% line và 70% branch |
| Gateway coverage | Route behavior là gate chính; chưa áp percentage gate |
| CI | Testcontainers chạy trên mọi pull request, không tách nightly |
| JWT phase này | Giữ HS256 và stateless logout |

Kế hoạch ban đầu dự kiến dùng MockMvc cho tầng HTTP/security. Khi implement, full HTTP integration trên random port được chọn để cùng lúc kiểm tra servlet container, security filter chain, exception serialization, JPA, Flyway và MySQL. Spring Security Test vẫn có trong test dependencies nhưng hiện không phải driver chính của HTTP tests.

## 3. Test pyramid và vai trò từng công cụ

| Tầng | Công cụ | Chạy khi nào | Điều nó chứng minh |
| --- | --- | --- | --- |
| Unit | JUnit 5, Mockito, AssertJ | `mvn test` và `mvn verify` | Service logic, timestamps, repository interaction, ownership branches, JWT generation/parsing |
| HTTP/security integration | Spring Boot random port, TestRestTemplate, Spring Security filter chain | `mvn verify` | Endpoint mapping, JSON, validation, authentication, authorization, status code, Problem Details |
| Database integration | Testcontainers MySQL 8.4, JPA, Flyway | `mvn verify` | SQL/schema thật, persistence, ordering và migration |
| Migration upgrade | Flyway API + JDBC + MySQL container | `mvn verify` của auth | V1 data sống qua upgrade và unique email được enforce |
| Gateway route contract | WebTestClient, WireMock | `mvn verify` của gateway | Route selection, path/query/body/status propagation và 404 |
| Runtime smoke | Docker Compose, curl | Sau runtime change | Public same-origin flow qua `http://localhost:3000`, malformed JWT và ownership |
| Quality gate | Surefire, Failsafe, JaCoCo | Maven `verify` và GitHub Actions | Không có test, test fail hoặc coverage thấp đều làm build fail |

## 4. Maven lifecycle và quy ước test

### 4.1 Surefire

- Version: `3.2.5`.
- Chạy các class `*Test` ở phase `test`.
- `failIfNoTests=true`, do đó module không được âm thầm pass khi test bị xóa/đổi tên sai.
- Dùng cho unit tests và gateway context test.

### 4.2 Failsafe

- Version: `3.2.5`.
- Chạy các class `*IT` ở phase `integration-test`.
- Đánh giá kết quả tại phase `verify`, bảo đảm cleanup lifecycle vẫn chạy trước khi Maven fail.
- `failIfNoTests=true` ở cả bốn module.

### 4.3 JaCoCo

- Version: `0.8.12`.
- Agent được gắn trước khi test, report và check chạy ở phase `verify`.
- Auth/post/comment fail build nếu:
  - line coverage nhỏ hơn `0.80`;
  - branch coverage nhỏ hơn `0.70`.
- Gateway sinh report nhưng chưa có percentage threshold vì module chỉ có bootstrap class; route integration tests mới là tín hiệu có ý nghĩa.

Các exclusions chỉ dành cho code không chứa quyết định nghiệp vụ: application bootstrap, DTO/request/model data holders. Controller, service, security filter và error handler vẫn nằm trong coverage gate. Không được mở rộng exclusions chỉ để làm số coverage đẹp hơn.

### 4.4 Các lệnh dùng đúng mục đích

```bash
# Chạy unit tests nhanh, không chạy *IT
cd <module>
./mvnw -B -ntp test

# Chạy toàn bộ unit + integration + coverage gate
./mvnw -B -ntp clean verify

# Gate chuẩn toàn repository
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin:$PATH \
./scripts/verify.sh
```

`scripts/verify.sh` chạy tuần tự gateway, auth, post, comment rồi `npm ci`, frontend test command và frontend production build.

## 5. API contract mà phase này khóa

### 5.1 Success status

| Operation | Status |
| --- | --- |
| Signup | 201 Created |
| Login/read | 200 OK |
| Create post/comment | 201 Created |
| Logout/delete | 204 No Content |

### 5.2 Error status

| Tình huống | Status | `code` dự kiến |
| --- | --- | --- |
| Bean validation lỗi | 400 | `VALIDATION_FAILED` |
| JSON không đọc được | 400 | `MALFORMED_REQUEST` |
| Thiếu authentication | 401 | `AUTHENTICATION_REQUIRED` |
| JWT malformed/expired/invalid claims | 401 | `INVALID_TOKEN` |
| Login sai | 401 | `BAD_CREDENTIALS` |
| Không phải owner | 403 | `NOT_OWNER` |
| Resource không tồn tại | 404 | `RESOURCE_NOT_FOUND` |
| Username trùng | 409 | `DUPLICATE_USERNAME` |
| Email trùng | 409 | `DUPLICATE_EMAIL` |

Error response dùng `application/problem+json` và có các field chuẩn `type`, `title`, `status`, `detail`, `instance`; custom property `code` luôn dùng cho machine-readable behavior. Validation error có thêm `fieldErrors` theo từng request field.

### 5.3 Validation rules

| Request | Rule |
| --- | --- |
| Signup username | 3–50 ký tự |
| Signup password | 8–72 ký tự |
| Signup email | Email hợp lệ, tối đa 255 ký tự |
| Login username/password | Không được blank |
| Post title | 1–255 ký tự |
| Post body | 1–10000 ký tự |
| Comment content | 1–10000 ký tự |
| Comment postId | Số dương |

### 5.4 JWT và identity contract

- Algorithm hiện tại: HS256.
- Claims được sử dụng: `sub`, `roles`, `iat`, `exp`.
- Post/comment filter yêu cầu `roles` là một list.
- Token parse/validation lỗi phải dừng filter chain và trả Problem Details 401.
- Post/comment username luôn lấy từ `Authentication.getName()`.
- Client gửi thêm `username` trong JSON không được ghi đè authenticated identity.
- Resource services chỉ xác thực token do auth phát hành; code tự phát hành JWT dư thừa đã bị loại khỏi `postservice` và `commentservice`.

## 6. Kế hoạch test ban đầu và trạng thái hiện tại

Legend:

- **Done**: có automated test đã chạy pass.
- **Partial**: behavior chính đã được test nhưng chưa đủ mọi case ban đầu.
- **Pending**: chưa có automated test riêng.

### 6.1 User auth service

| ID | Case trong kế hoạch | Tầng | Trạng thái | Test/file hiện tại |
| --- | --- | --- | --- | --- |
| AUTH-U01 | Load user và map role thành `ROLE_*` | Unit | Done | `CustomUserDetailsServiceTest.loadsRolesWithSpringPrefix` |
| AUTH-U02 | Unknown user bị từ chối | Unit | Done | `rejectsUnknownUser` |
| AUTH-U03 | Signup service gán default role | Unit | Done | `savesWithDefaultRole` |
| AUTH-U04 | Thiếu default role làm signup fail rõ ràng | Unit | Done | `failsWhenDefaultRoleIsMissing` |
| AUTH-J01 | JWT chứa đúng subject/roles/expiration | Unit | Done | `JwtUtilTest.generatesExpectedIdentityAndRoles` |
| AUTH-J02 | JWT hết hạn bị từ chối | Unit | Done | `rejectsExpiredToken` |
| AUTH-J03 | JWT malformed bị từ chối | Unit | Done | `rejectsMalformedToken` |
| AUTH-J04 | JWT sai chữ ký | Unit/HTTP | Pending | Chưa có test riêng |
| AUTH-J05 | JWT thiếu claim bắt buộc | Unit/HTTP | Partial | Resource services test thiếu `roles`; auth chưa test thiếu `sub`/claims |
| AUTH-I01 | Signup 201, response không lộ password, DB lưu BCrypt | HTTP + MySQL | Done | `UserAuthServiceIT.signupLoginAndReadCurrentUser` |
| AUTH-I02 | Login 200 và dùng token đọc current user | HTTP + MySQL | Done | cùng test trên |
| AUTH-I03 | Duplicate email trả 409 Problem Details | HTTP + MySQL | Done | `duplicateEmailAndBadCredentialsUseProductionStatusCodes` |
| AUTH-I04 | Duplicate username trả 409 | HTTP + MySQL | Pending | Production code có branch, chưa có test riêng |
| AUTH-I05 | Bad credentials trả 401 | HTTP + MySQL | Done | cùng test duplicate email |
| AUTH-I06 | Payload validation trả 400 và `fieldErrors` | HTTP + MySQL | Done | `validatesPayloadAndRejectsInvalidJwt` |
| AUTH-I07 | Malformed JWT trả 401 `INVALID_TOKEN` | HTTP + MySQL | Done | cùng test validation |
| AUTH-I08 | `/user` thiếu token trả 401 | HTTP | Pending | Chưa có test riêng |
| AUTH-I09 | `/logout` authenticated trả 204 | HTTP | Pending | Chưa có test riêng |
| AUTH-I10 | `/logout` thiếu token trả 401 | HTTP | Pending | Chưa có test riêng |
| AUTH-I11 | Malformed JSON trả 400 `MALFORMED_REQUEST` | HTTP | Pending | Handler đã implement, chưa có test riêng |
| AUTH-M01 | Flyway migrate database trống lên latest | MySQL | Done | Spring context trong `UserAuthServiceIT` |
| AUTH-M02 | Flyway V1 → latest giữ dữ liệu | Migration | Done | `AuthMigrationIT.migratesV1DataToLatestAndEnforcesUniqueEmail` |
| AUTH-M03 | Latest schema enforce unique email | Migration | Done | cùng migration test |

### 6.2 Post service

| ID | Case trong kế hoạch | Tầng | Trạng thái | Test/file hiện tại |
| --- | --- | --- | --- | --- |
| POST-U01 | Add post gán timestamp từ Clock | Unit | Done | `PostServiceTest.addsTimestampBeforeSaving` |
| POST-U02 | Owner xóa được post | Unit | Done | `ownerCanDelete` |
| POST-U03 | Non-owner bị 403 | Unit | Done | `nonOwnerCannotDelete` |
| POST-U04 | Missing post bị 404 | Unit | Done | `missingPostReturnsNotFound` |
| POST-I01 | Create trả 201 và lấy username từ token | HTTP + MySQL | Done | `PostServiceIT.createsWithAuthenticatedIdentityAndEnforcesOwnership` |
| POST-I02 | Request body không giả mạo được username | HTTP + MySQL | Done | cùng test trên gửi `username=victim`, kết quả vẫn là `alice` |
| POST-I03 | Non-owner delete trả 403 và dữ liệu còn nguyên | HTTP + MySQL | Done | cùng test trên |
| POST-I04 | Owner delete trả 204 | HTTP + MySQL | Done | cùng test trên |
| POST-I05 | Invalid title/body trả 400 | HTTP + MySQL | Done | `validatesPayloadAndRejectsBadTokens` |
| POST-I06 | Malformed JWT trả 401 | HTTP + MySQL | Done | cùng test validation |
| POST-I07 | JWT thiếu `roles` trả 401 | HTTP + MySQL | Done | cùng test validation |
| POST-I08 | List trả newest first | HTTP + MySQL | Done | `returnsNewestPostsFirstAndMissingDeleteIsNotFound` |
| POST-I09 | Delete missing ID trả 404 | HTTP + MySQL | Done | cùng test ordering |
| POST-I10 | Thiếu Authorization trả 401 | HTTP | Pending | Chưa có test riêng |
| POST-I11 | JWT expired/sai chữ ký trả 401 | HTTP | Pending | JWT expired được test tại auth unit; chưa test tại post filter |
| POST-I12 | Malformed JSON trả 400 | HTTP | Pending | Handler đã implement, chưa có test riêng |
| POST-M01 | Flyway database trống + JPA validate | MySQL | Done | Context startup của `PostServiceIT` |

### 6.3 Comment service

| ID | Case trong kế hoạch | Tầng | Trạng thái | Test/file hiện tại |
| --- | --- | --- | --- | --- |
| COMMENT-U01 | Add comment gán timestamp từ Clock | Unit | Done | `CommentServiceTest.addsTimestampBeforeSaving` |
| COMMENT-U02 | Lookup dùng postId và newest-first repository method | Unit | Done | `delegatesOrderedPostLookup` |
| COMMENT-U03 | Owner xóa được comment | Unit | Done | `ownerCanDelete` |
| COMMENT-U04 | Non-owner bị 403 | Unit | Done | `nonOwnerCannotDelete` |
| COMMENT-U05 | Missing comment bị 404 | Unit | Done | `missingCommentReturnsNotFound` |
| COMMENT-I01 | Create trả 201 và lấy username từ token | HTTP + MySQL | Done | `CommentServiceIT.createsWithAuthenticatedIdentityAndEnforcesOwnership` |
| COMMENT-I02 | Request body không giả mạo được username | HTTP + MySQL | Done | cùng test trên |
| COMMENT-I03 | Non-owner delete trả 403 và dữ liệu còn nguyên | HTTP + MySQL | Done | cùng test trên |
| COMMENT-I04 | Owner delete trả 204 | HTTP + MySQL | Done | cùng test trên |
| COMMENT-I05 | Invalid postId/content trả 400 | HTTP + MySQL | Done | `validatesPayloadAndRejectsBadTokens` |
| COMMENT-I06 | Malformed JWT trả 401 | HTTP + MySQL | Done | cùng test validation |
| COMMENT-I07 | JWT thiếu `roles` trả 401 | HTTP + MySQL | Done | cùng test validation |
| COMMENT-I08 | Chỉ trả comment đúng postId và newest first | HTTP + MySQL | Done | `returnsOnlyRequestedPostNewestFirstAndMissingDeleteIsNotFound` |
| COMMENT-I09 | Delete missing ID trả 404 | HTTP + MySQL | Done | cùng test ordering |
| COMMENT-I10 | Thiếu Authorization trả 401 | HTTP | Pending | Chưa có test riêng |
| COMMENT-I11 | JWT expired/sai chữ ký trả 401 | HTTP | Pending | Chưa có test riêng tại comment filter |
| COMMENT-I12 | Malformed JSON trả 400 | HTTP | Pending | Handler đã implement, chưa có test riêng |
| COMMENT-M01 | Flyway database trống + JPA validate | MySQL | Done | Context startup của `CommentServiceIT` |

### 6.4 API gateway

| ID | Case trong kế hoạch | Tầng | Trạng thái | Test/file hiện tại |
| --- | --- | --- | --- | --- |
| GW-C01 | Application context load | Context | Done | `ApiGatewayServerApplicationTests` |
| GW-I01 | `/api/auth/**` đi tới auth, không rewrite path | Route contract | Done | `GatewayRoutesIT.routesAuthPathWithoutRewritingIt` |
| GW-I02 | Auth request giữ query/body/status | Route contract | Done | cùng test auth |
| GW-I03 | `/api/post/**` chỉ đi tới post upstream | Route contract | Done | `routesPostPathToPostOnly` |
| GW-I04 | `/api/comment/**` đi tới comment và giữ status | Route contract | Done | `routesCommentPathAndPropagatesStatus` |
| GW-I05 | Unknown path trả 404 | Route contract | Done | `unknownPathIsNotRouted` |
| GW-I06 | Upstream unavailable trả lỗi có timeout, không treo | Route contract | Pending | Chưa có test riêng hoặc timeout policy riêng |

## 7. Test files và trách nhiệm

### 7.1 Auth

- `userauthservice/src/test/java/com/pnimac/auth/util/JwtUtilTest.java`: JWT unit contract.
- `userauthservice/src/test/java/com/pnimac/auth/model/service/CustomUserDetailsServiceTest.java`: role/default-role/service behavior.
- `userauthservice/src/test/java/com/pnimac/auth/UserAuthServiceIT.java`: API + security + persistence qua MySQL thật.
- `userauthservice/src/test/java/com/pnimac/auth/AuthMigrationIT.java`: migrate V1 data lên latest.
- `userauthservice/src/test/resources/application-it.properties`: JPA validate, Flyway location, test JWT config.
- `userauthservice/src/test/resources/docker-java.properties`: `api.version=1.44` cho Docker Engine 29 compatibility.

### 7.2 Post

- `postservice/src/test/java/com/pnimac/post/service/PostServiceTest.java`: timestamp và ownership unit tests.
- `postservice/src/test/java/com/pnimac/post/PostServiceIT.java`: endpoint, identity, validation, JWT, ordering và ownership trên MySQL.
- `postservice/src/test/resources/application-it.properties`: integration profile.
- `postservice/src/test/resources/docker-java.properties`: Docker API compatibility.

### 7.3 Comment

- `commentservice/src/test/java/com/pnimac/comment/service/CommentServiceTest.java`: timestamp, repository delegation và ownership unit tests.
- `commentservice/src/test/java/com/pnimac/comment/CommentServiceIT.java`: endpoint, identity, validation, JWT, filtering/order và ownership trên MySQL.
- `commentservice/src/test/resources/application-it.properties`: integration profile.
- `commentservice/src/test/resources/docker-java.properties`: Docker API compatibility.

### 7.4 Gateway

- `api-gateway-server/src/test/java/com/pnimac/gateway/api_gateway_server/ApiGatewayServerApplicationTests.java`: context test.
- `api-gateway-server/src/test/java/com/pnimac/gateway/GatewayRoutesIT.java`: ba WireMock upstream và WebTestClient route assertions.

## 8. Production code đã thay đổi để hỗ trợ contract

Đây không chỉ là thêm test. Test đã làm lộ ra các contract cần chuẩn hóa, nên phase có các thay đổi production sau.

### 8.1 Auth

- `LoginRequest` và `RegisterRequest` dùng Jakarta Validation.
- Controller nhận typed DTO thay vì raw map.
- Signup trả 201, login trả 200, logout trả 204.
- Signup kiểm tra cả username và email; V2 Flyway thêm unique constraint cho email.
- Bad credentials, duplicate data, missing resource và validation dùng status/code rõ ràng.
- Thêm `ApiException`, `ApiExceptionHandler`, `ProblemSupport`, authentication entry point và access-denied handler.
- JWT filter bắt lỗi parse/expired và trả 401 Problem Details thay vì để exception thành 500.
- `JwtUtil.extractAllClaims` được public để contract claims có thể kiểm tra.

### 8.2 Post và comment

- Request DTO có validation constraints.
- Controller lấy username từ `Authentication`, không lấy từ client.
- Create trả 201; delete trả 204.
- Security filter yêu cầu token hợp lệ và `roles` list hợp lệ.
- Lỗi auth/authorization/validation/resource dùng Problem Details.
- Service chuyển sang constructor injection và nhận `Clock` bean để timestamp test deterministic.
- Comment repository dùng `findByPostIdOrderByCreatedAtDesc` để contract ordering nằm ngay tại query.
- Xóa code phát hành JWT không thuộc trách nhiệm của resource services.

### 8.3 Frontend compatibility

`blog-client/src/context/AuthContext.js` đọc `error.response.data.detail` trước `message`, giữ fallback cho response cũ. Không có frontend test mới trong phase này.

## 9. Testcontainers và MySQL/Flyway strategy

### 9.1 Container lifecycle

Mỗi business service integration suite khai báo static `MySQLContainer<?>` dùng image `mysql:8.4.0` và `@ServiceConnection`. Spring Boot tự map JDBC URL, username và password động. Test không phụ thuộc port cố định hoặc database local.

### 9.2 Schema validation

Integration profile dùng:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Hibernate không được tự sửa schema. Flyway phải tạo schema đúng trước khi JPA context pass.

### 9.3 Auth upgrade test

`AuthMigrationIT` thực hiện tuần tự:

1. Start MySQL 8.4 database trống.
2. Migrate Flyway chỉ tới target V1.
3. Insert user data theo schema V1.
4. Migrate lên latest.
5. Validate Flyway history.
6. Xác nhận dữ liệu cũ còn tồn tại.
7. Xác nhận duplicate email bị database constraint từ chối.

### 9.4 Docker Engine 29 compatibility

Spring Boot 3.3.1 hiện quản lý Testcontainers 1.19.8, vốn dùng Docker API mặc định quá cũ cho Docker Engine 29. Vì vậy ba module Testcontainers có:

```properties
api.version=1.44
```

trong `src/test/resources/docker-java.properties`. Không xóa file này khi chưa nâng dependency và kiểm chứng lại trên Docker 29.

## 10. GitHub Actions backend gate

Workflow: `.github/workflows/backend-ci.yml`.

### 10.1 Trigger và permissions

- `pull_request`.
- Push vào `main`.
- Manual `workflow_dispatch`.
- Chỉ cấp `contents: read`.
- Pull request run cũ bị cancel khi có commit mới; push run không bị cancel.

### 10.2 Matrix

Một matrix job chạy độc lập:

- `userauthservice`
- `postservice`
- `commentservice`
- `api-gateway-server`

Mỗi job:

1. Checkout source.
2. Setup Temurin JDK 17 và Maven cache.
3. Chạy `./mvnw -B -ntp clean verify` trong module.
4. Upload Surefire, Failsafe và JaCoCo reports với `if: always()`.
5. Timeout sau 20 phút.

Official actions được pin bằng full commit SHA, không dùng mutable tag trong `uses`.

### 10.3 Aggregate gate

Job `backend-test-gate` chạy với `if: always()` và chỉ pass khi toàn bộ matrix có result `success`. Điều này tránh trường hợp một matrix job fail/skip nhưng check tổng vẫn xanh.

Workflow file đã được implement nhưng chưa có bằng chứng remote Actions run trong workspace này vì chưa commit/push. Repository administrator vẫn phải cấu hình branch protection để tên check `Backend test gate` trở thành required check; workflow không thể tự thay đổi repository settings.

## 11. Bằng chứng đã chạy

### 11.1 Test count

| Module | Unit/context | Integration | Tổng |
| --- | ---: | ---: | ---: |
| `userauthservice` | 7 | 4 | 11 |
| `postservice` | 4 | 3 | 7 |
| `commentservice` | 5 | 3 | 8 |
| `api-gateway-server` | 1 | 4 | 5 |
| **Tổng** | **17** | **14** | **31** |

Tất cả report tại lần chạy acceptance có 0 failures, 0 errors và 0 skipped.

### 11.2 Coverage

| Module | Line | Branch | Gate |
| --- | ---: | ---: | --- |
| `userauthservice` | 92.26% | 70.00% | Pass |
| `postservice` | 93.28% | 72.22% | Pass |
| `commentservice` | 92.54% | 72.22% | Pass |
| `api-gateway-server` | 33.33% | Không có branch có ý nghĩa | Report only; 4 route IT pass |

### 11.3 Commands và outcome

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin:$PATH \
./scripts/verify.sh
```

Outcome: bốn Maven `clean verify` pass bằng Java 17; 31 Java tests pass; JaCoCo gates pass; frontend production build pass. Frontend vẫn chưa có test và hiện dùng `--passWithNoTests`.

```bash
docker compose up --build -d
docker compose ps
```

Outcome: MySQL và năm application containers đều healthy.

```bash
./scripts/smoke-test.sh
```

Outcome:

- Frontend public origin reachable.
- Hai user signup/login thành công.
- User A tạo/read post và comment qua gateway.
- Malformed JWT trả 401.
- User B không xóa được post/comment của user A và nhận 403.
- User A xóa cleanup thành công.

## 12. Khoảng trống còn lại của chính backend test phase

Các mục dưới đây nằm trong ý tưởng production-grade ban đầu nhưng chưa có automated test riêng. Agent tiếp theo nên hoàn tất chúng trước khi tuyên bố backend contract coverage đầy đủ:

1. Auth duplicate username trả 409 `DUPLICATE_USERNAME`.
2. `/api/auth/user` và `/api/auth/logout` với missing/valid authentication.
3. Malformed request JSON cho auth/post/comment trả 400 `MALFORMED_REQUEST`.
4. JWT sai chữ ký cho cả auth và resource-service filters.
5. JWT expired qua HTTP filter của post/comment, không chỉ auth `JwtUtil` unit test.
6. Token thiếu `sub`, `exp` hoặc claim type sai.
7. Missing Authorization header cho post/comment trả 401 Problem Details.
8. Gateway upstream unavailable/timeout behavior.
9. Race condition signup trùng username/email: database constraint phải là lớp bảo vệ cuối cùng và exception phải map thành 409.
10. Explicit Flyway upgrade tests cho post/comment khi chúng có migration V2 trở lên.

Ngoài backend phase:

- Frontend component tests và Playwright E2E vẫn chưa làm.
- `scripts/verify.sh` vẫn cho frontend pass khi không có test.
- Frontend dependency tree tại acceptance run báo 71 audit findings.
- GitHub Actions chưa có remote run evidence và branch protection chưa được cấu hình trong source code.

## 13. Thứ tự đề xuất để hoàn thiện phần còn thiếu

### Batch A — Đóng security/HTTP gaps

1. Bổ sung parameterized HTTP tests cho missing token, malformed token, expired token, wrong signature và missing/wrong-type claims.
2. Bổ sung malformed JSON tests cho cả ba service.
3. Bổ sung authenticated/unauthenticated `/user` và `/logout` tests.
4. Bổ sung duplicate username và concurrent duplicate signup test.
5. Chạy riêng từng module bằng JDK 17 `clean verify`.

### Batch B — Gateway resilience contract

1. Chốt connect/response timeout production values.
2. Cho WireMock upstream dừng hoặc delayed response.
3. Assert status/error behavior và upper bound thời gian response.
4. Không viết test timing quá chặt gây flaky CI.

### Batch C — Frontend và full E2E phase

1. Viết Jest/React Testing Library tests cho auth/post/comment UI.
2. Bỏ `--passWithNoTests`.
3. Viết Playwright flow qua gateway/public origin.
4. Dùng browser E2E để kiểm signup/login/create/delete và negative ownership ở mức người dùng.

## 14. Quy tắc cho agent tiếp theo

- Đọc tài liệu kiến trúc mục 4, 6, 17 và `CHANGELOG.md` trước khi sửa.
- Không reintroduce Eureka, Config Server hoặc H2 integration baseline.
- Không bypass Testcontainers/coverage bằng skip flags trong CI.
- Không hạ threshold hoặc thêm coverage exclusion nếu chưa có lý do kiến trúc được ghi lại.
- Mỗi integration test phải tự quản lý dữ liệu và không phụ thuộc thứ tự test.
- Không dùng sleep để tạo ordering; seed timestamp xác định hoặc dùng injected `Clock`.
- Security negative test phải kiểm cả status, error `code` và dữ liệu không bị thay đổi.
- Khi thêm migration, test cả empty database và upgrade từ version ngay trước đó.
- Nếu thay API contract, sửa đồng thời backend tests, gateway tests, smoke test, frontend compatibility và tài liệu này.
- Sau source/config changes phải chạy `scripts/verify.sh`.
- Sau runtime changes phải rebuild Compose, chờ healthy và chạy `scripts/smoke-test.sh`.
- Chỉ ghi “Done” khi có test hoặc command thực tế làm bằng chứng.

## 15. Definition of Done của phase

| Acceptance criterion | Trạng thái | Ghi chú |
| --- | --- | --- |
| Bốn module build độc lập bằng Maven Wrapper | Done | Đã chạy qua `scripts/verify.sh` |
| Gate chạy bằng JDK 17 | Done local/configured CI | Local acceptance dùng Java 17; CI pin Temurin 17 |
| MySQL 8.4 Testcontainers trên PR | Implemented, remote evidence pending | Workflow đã có, chưa push/run |
| Surefire/Failsafe tách unit và integration | Done | 17 unit/context + 14 integration |
| Auth/post/comment đạt 80% line, 70% branch | Done | Số liệu tại mục 11 |
| Invalid JWT không thành 500 | Done cho malformed/missing-roles paths | Wrong signature và HTTP expired paths còn thiếu |
| Ownership và authenticated identity có negative tests | Done | Unit, HTTP IT và Compose smoke đều có |
| Flyway empty DB và auth upgrade test | Done | Post/comment chỉ có empty DB cho tới khi có V2 |
| Gateway ba route có integration contract | Done | WireMock + WebTestClient |
| Gateway unavailable timeout test | Pending | Xem mục 12 |
| Docker Compose smoke vẫn pass | Done | Sáu container healthy, smoke pass |
| CI aggregate required check | Implemented in workflow | Branch protection remote còn phải cấu hình |
| Toàn bộ checklist production ban đầu | Partial | Các gaps cụ thể nằm tại mục 12 |
