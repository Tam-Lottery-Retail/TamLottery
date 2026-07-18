# TamLottery

Ứng dụng quản lý lô vé, giao vé cho seller, trả vé, thất thoát, giao dịch tiền và đối soát cuối ngày. Repository gồm Spring Boot backend và React frontend.

## Yêu cầu

- Java 25
- Node.js 22.13 trở lên khi phát triển frontend local
- Docker Desktop hoặc MySQL 8.4
- `JAVA_HOME` trỏ tới JDK 25

## Chạy local

Tạo database `tam_lottery`, sau đó cấu hình biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và `JWT_SECRET`.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:SPRING_PROFILES_ACTIVE='local'
$env:JWT_SECRET='BASE64_ENCODED_SECRET_AT_LEAST_32_BYTES'
.\mvnw.cmd spring-boot:run
```

Muốn tạo store và owner lần đầu, cấu hình các biến `BOOTSTRAP_*` trong `.env.example` và đặt `BOOTSTRAP_ENABLED=true`.

## Chạy với Aiven MySQL

Tạo `.env` với `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Nên dùng JDBC URL không chứa credential;
username/password luôn để ở hai biến riêng. Ứng dụng vẫn chấp nhận service URI `mysql://...` của Aiven,
nhưng sẽ loại credential khỏi URL trước khi khởi tạo datasource:

```powershell
.\scripts\run-aiven.ps1
```

Profile `aiven` mặc định giới hạn HikariCP ở 5 connection để phù hợp instance nhỏ. Production nên truyền thêm
`JWT_SECRET` và các biến `BOOTSTRAP_*` qua secret manager của nền tảng chạy ứng dụng.

## Chạy frontend local

Backend cần chạy ở port `8080`.

```powershell
Set-Location frontend
npm install
npm run dev
```

Frontend mặc định mở tại `http://localhost:3000` và proxy `/api` về backend.

## Chạy Docker

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Compose chạy backend và frontend; backend kết nối Aiven MySQL, không tạo MySQL local. Không commit `.env`.
`JWT_SECRET` là bắt buộc. Giao diện mặc định tại `http://localhost:3000`; backend health tại `GET http://localhost:8080/actuator/health`.

## Kiểm thử

Unit test không cần Docker:

```powershell
.\mvnw.cmd test
```

Integration test chạy Flyway và Hibernate schema validation trên MySQL 8.4 Testcontainers:

```powershell
.\mvnw.cmd verify -Pintegration-tests
```

Integration test tự bỏ qua khi Docker daemon không khả dụng.

## Luồng API chính

1. `POST /api/v1/auth/login`
2. Tạo seller, agency và draw.
3. Tạo batch rồi `POST /api/v1/batches/{id}/confirm`.
4. Tạo allocation rồi `POST /api/v1/allocations/{id}/issue`.
5. Tạo return/adjustment và xác nhận hoặc duyệt.
6. Seller tạo cash transaction; manager post.
7. Xem `/api/v1/reconciliations/preview`, chốt seller trước, sau đó chốt store.

Tiền dùng đơn vị đồng (`BIGINT`), timestamp lưu UTC và ngày nghiệp vụ dùng `LocalDate`.
