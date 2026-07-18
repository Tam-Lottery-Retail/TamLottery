# TamLottery — Ghi chú phỏng vấn

## Bài giới thiệu 60–90 giây

> TamLottery là hệ thống quản lý vận hành tôi xây dựng cho cửa hàng vé số của người nhà. Trước đây cửa hàng ghi nhận vé và tiền thủ công nên khó kiểm soát vé đã bán, vé trả, thất thoát và chênh lệch cuối ngày.
>
> Hệ thống có ba vai trò Owner, Manager và Seller. Luồng chính bắt đầu từ nhận vé của đại lý cấp 1, giao vé cho seller, ghi nhận trả hoặc thất thoát, thu tiền và cuối cùng đối soát seller trước khi đối soát toàn cửa hàng.
>
> Backend được xây bằng Java 25, Spring Boot, Spring Security, JPA và MySQL trên Aiven. Tôi dùng transaction, pessimistic locking, entity versioning và unique constraints để tránh dữ liệu trùng hoặc phân bổ quá tồn. Hệ thống được container hóa bằng Docker và có unit/integration test với MySQL Testcontainers.
>
> Hiện tại dự án ở giai đoạn MVP, tập trung vào luồng bán vé và đối soát; kết quả xổ số, trả thưởng và audit log là các phase tiếp theo.

## 1. Yêu cầu thực tế

Mỗi ngày cửa hàng:

```text
Nhận vé từ đại lý cấp 1
→ Bán tại quầy hoặc giao seller
→ Nhận lại vé chưa bán
→ Ghi nhận thất thoát
→ Nhận tiền seller giao
→ Đối soát cuối ngày
```

Vấn đề của cách làm thủ công:

- Không biết chính xác seller đang giữ bao nhiêu vé.
- Dễ tính sai vé bán, vé trả và thất thoát.
- Tiền seller giao khó đối chiếu với doanh thu dự kiến.
- Khó xác định ai đã xác nhận dữ liệu.
- Tổng hợp cuối ngày mất thời gian.

Người dùng:

- `OWNER`: tài khoản, toàn bộ dữ liệu và duyệt chênh lệch.
- `MANAGER`: nhận/giao vé, duyệt trả/mất, ghi sổ tiền và chốt.
- `SELLER`: dữ liệu của chính mình, trả/mất, giao tiền và xem đối soát.

## 2. Domain model

| Entity | Ý nghĩa |
|---|---|
| `Store` | Phạm vi sở hữu dữ liệu |
| `UserAccount` | Danh tính đăng nhập và role |
| `Seller` | Hồ sơ nghiệp vụ gắn với vé, tiền và đối soát |
| `Agency` | Đại lý cấp 1 |
| `LotteryDraw` | Kỳ vé được tự ghi nhận từ lô nhận |
| `LotteryBatch/Line` | Lô và từng dòng vé cửa hàng nhận |
| `TicketAllocation/Line` | Vé giao seller |
| `TicketReturn` | Seller trả cửa hàng hoặc cửa hàng trả đại lý |
| `InventoryAdjustment` | Thất thoát, hư hỏng, tìm thấy hoặc sửa sai |
| `CashTransaction` | Thu/chi và trạng thái ghi sổ |
| `DailySales` | Snapshot doanh thu |
| `DailyReconciliation` | Kết quả đối soát seller/store |

`UserAccount` và `Seller` tách riêng vì một bên là authentication/authorization, một bên là hồ sơ nghiệp vụ. Seller có thể tồn tại mà chưa có tài khoản; khi cần đăng nhập thì liên kết một-một với user có role `SELLER`.

Giá bán nằm tại `LotteryBatchLine` để lịch sử doanh thu không thay đổi khi giá bán mới được áp dụng cho lô khác.

## 3. Quy tắc nghiệp vụ

```text
Seller sold = allocated - seller returns - approved seller losses
Store sold  = received - agency returns - all approved losses
Expected revenue = Σ(sold quantity × unit sale price)
Difference = actual received - expected revenue
```

Hai loại trả:

- `SELLER_TO_STORE`: chuyển tồn seller về cửa hàng.
- `STORE_TO_AGENCY`: giảm tổng vé cửa hàng.

Tiền:

- Giao dịch mới là `PENDING`.
- Manager chuyển `PENDING → POSTED`.
- Chỉ `POSTED` được tính vào đối soát.
- Chưa gắn reconciliation thì có thể chuyển `VOID`.
- Thu bán vé bắt buộc `IN`; hoàn tiền/chi phí/thanh toán đại lý bắt buộc `OUT`.

Đối soát:

- Chênh lệch bằng 0 → `CLOSED`.
- Có chênh lệch → `REVIEW_REQUIRED`, bắt buộc lý do.
- Owner duyệt hoặc từ chối để mở lại dữ liệu.
- Chốt seller trước, sau đó chốt store.
- `DailySales` là snapshot hệ thống tạo, không có API chỉnh sửa trực tiếp.

Ràng buộc:

- Username duy nhất toàn hệ thống.
- Mã seller duy nhất trong store.
- Một user chỉ liên kết một seller.
- `storeId` và `sellerId` lấy từ JWT, không tin giá trị tùy ý từ client.

## 4. Quyết định kỹ thuật

### Modular monolith

Quy mô hiện tại chưa cần microservice. Modular monolith giữ deployment và transaction đơn giản nhưng vẫn tách module:

```text
identity → masterdata → inventory → cash → reconciliation
```

### MySQL

Inventory, cash và reconciliation có quan hệ chặt và cần ACID transaction. MySQL phù hợp hơn việc thêm Kafka hoặc NoSQL ở MVP. Kafka chỉ đáng cân nhắc khi có nhu cầu event-driven, nhiều cửa hàng hoặc xử lý bất đồng bộ thực sự.

### Security

- Access token 15 phút.
- Refresh token 30 ngày, chỉ lưu hash và rotate.
- Role, `storeId`, `sellerId` nằm trong JWT.
- Backend luôn áp dụng store/seller isolation.

### Concurrency

Pessimistic lock được dùng ở thao tác tồn kho nhạy cảm. Nếu hai request cùng giao 70 vé khi tồn chỉ còn 100, request đầu thành công; request sau chờ, đọc lại tồn và nhận `INVENTORY_NOT_ENOUGH`.

`@Version` phát hiện cập nhật đồng thời ở entity. Unique constraint tại database là lớp bảo vệ cuối cùng trước race condition.

### Theo dõi vé theo số lượng

MVP không tạo từng record cho từng vé. Batch line lưu số lượng và dải serial tùy chọn. Thiết kế đơn giản hơn nhưng chưa truy vết được từng serial.

## 5. Trade-off

| Quyết định | Lợi ích | Đánh đổi |
|---|---|---|
| Modular monolith | Dễ phát triển/deploy | Chưa scale riêng module |
| MySQL | Transaction và quan hệ tốt | Không phải event store |
| Pessimistic lock | Chống oversell chắc chắn | Giảm throughput khi tranh cùng dòng |
| JWT stateless | API dễ scale | Token cũ có thể còn hiệu lực tối đa 15 phút |
| Theo dõi số lượng | Ít record, phù hợp cửa hàng nhỏ | Không truy vết từng vé |
| DailySales immutable | Số liệu khớp nguồn | Sửa sai phải reopen/reject |
| User/Seller tách riêng | Linh hoạt | UX cần bước liên kết |
| Aiven | Giảm quản trị database | Phụ thuộc mạng/dịch vụ ngoài |
| Chưa dùng Kafka | Ít phức tạp | Chưa có event-driven flow |
| Chưa có AuditLog | Ra MVP nhanh | Chưa truy vết đầy đủ |

Nguyên tắc trả lời:

> Tôi không chọn công nghệ mạnh nhất, mà chọn giải pháp phù hợp với quy mô và rủi ro thực tế. Với cửa hàng nhỏ, tính đúng của tồn kho và doanh thu quan trọng hơn throughput rất lớn.

## 6. Cách kiểm thử

Unit test:

- Công thức vé bán seller/store.
- Doanh thu và chênh lệch.
- Signed cash amount.
- State transition của DailySales/Reconciliation.

Integration test bằng MySQL Testcontainers:

- Flyway và schema validation.
- JWT, refresh rotation, role và store isolation.
- Unique username/mã seller.
- Tạo seller + account nguyên tử và rollback khi trùng.
- Seller chỉ thấy dữ liệu của mình.
- Luồng nhận → giao → trả/mất → tiền → đối soát.
- Hai request phân bổ đồng thời không oversell.

Release `v0.1.0` có 12 unit test và 12 integration test, frontend lint/build và Docker healthcheck.

## Câu kết

> Phần tôi tập trung nhất không phải CRUD mà là tính nhất quán giữa tồn vé, tiền seller giao và doanh thu cuối ngày. Các rủi ro chính như dữ liệu trùng, seller xem nhầm dữ liệu, phân bổ quá tồn và chốt sai doanh thu được bảo vệ bằng validation, transaction, database constraint, locking và automated test.
