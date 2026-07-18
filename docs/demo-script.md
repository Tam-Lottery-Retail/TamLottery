# Kịch bản video demo TamLottery (2–3 phút)

## Chuẩn bị

- Chạy backend và frontend bằng Docker.
- Seed profile `demo` một lần.
- Mở `http://localhost:3000`.
- Chuẩn bị hai tài khoản `demo.owner.v01` và `demo.seller.v01`.

## 0:00–0:20 — Giới thiệu bài toán

> TamLottery là hệ thống quản lý vận hành cho cửa hàng vé số cấp 2. Hệ thống theo dõi vé từ lúc nhận của đại lý, giao seller, trả hoặc thất thoát, giao tiền và đối soát cuối ngày.

Hiển thị màn hình đăng nhập, sau đó đăng nhập bằng owner demo.

## 0:20–0:55 — Dashboard owner và dữ liệu đầu vào

Hiển thị dashboard owner:

- Vé nhận trong ngày.
- Vé đã giao.
- Doanh thu dự kiến và chênh lệch.
- Các việc đang chờ xử lý.

Mở **Đại lý & kỳ vé** để chỉ ra đại lý cấp 1 và kỳ vé được hệ thống tự ghi nhận khi nhận lô.

Mở **Nhận vé** và giải thích lô mẫu gồm 150 vé, giá bán 10.000đ. Nhắc nhanh rằng hệ thống hỗ trợ nhập CSV/XLSX có preview.

## 0:55–1:30 — Giao, trả và thất thoát

Mở **Giao vé**:

- Seller nhận 80 vé.

Mở **Trả & thất thoát**:

- Seller trả 10 vé về cửa hàng.
- Hai vé thất thoát đã được manager duyệt.
- Cửa hàng trả 80 vé tồn về đại lý cấp 1.

Giải thích công thức:

```text
Seller sold = 80 - 10 - 2 = 68 vé
Expected revenue = 68 × 10.000 = 680.000đ
```

## 1:30–1:55 — Tiền và đối soát

Mở **Giao dịch tiền**:

- Seller đã giao 680.000đ.
- Giao dịch đã được manager chuyển từ `PENDING` sang `POSTED`.

Mở **Đối soát**, chọn seller và xem preview:

- Vé bán: 68.
- Dự kiến: 680.000đ.
- Thực nhận: 680.000đ.
- Chênh lệch: 0đ.

Giải thích seller phải được chốt trước khi chốt toàn cửa hàng.

## 1:55–2:25 — Dashboard seller và phân quyền

Đăng xuất owner, đăng nhập `demo.seller.v01`.

Hiển thị dashboard **Của tôi hôm nay**:

- Chỉ có vé và doanh thu của seller đang đăng nhập.
- Seller có thể giao tiền, trả vé, báo thất thoát và xem đối soát cá nhân.
- Seller không thấy màn hình nhận vé, giao vé cho người khác hoặc quản lý nhân sự.

## 2:25–2:50 — Điểm kỹ thuật

> Backend sử dụng Java 25, Spring Boot, JPA, Spring Security và MySQL trên Aiven. Dữ liệu được cô lập theo store và seller từ JWT. Các thao tác tồn kho dùng transaction, pessimistic locking, entity versioning và unique constraints để tránh oversell hoặc ghi trùng. Luồng chính được kiểm thử bằng MySQL Testcontainers.

## 2:50–3:00 — Kết thúc

> Đây là MVP tập trung vào quản lý vé, tiền và đối soát. Các phase tiếp theo là kết quả xổ số, trả thưởng và audit log.
