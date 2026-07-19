# TamLottery production operations runbook

Tài liệu này là checklist vận hành cho TamLottery khi dùng Aiven MySQL và Docker Compose. Mục tiêu là phát hiện sự cố sớm, khôi phục được dữ liệu và luôn để lại dấu vết kiểm toán.

## 1. Mục tiêu phục hồi

Đối với một cửa hàng nhỏ, mục tiêu ban đầu:

| Chỉ số | Mục tiêu |
|---|---|
| RPO (mất dữ liệu tối đa) | Không quá 24 giờ; ưu tiên thấp hơn nếu gói Aiven hỗ trợ PITR tốt hơn |
| RTO (thời gian khôi phục) | Không quá 2 giờ trong giờ hoạt động |
| Retention logical backup | 14 bản hằng ngày và 8 bản hằng tuần |
| Restore drill | Ít nhất mỗi tháng và trước thay đổi migration lớn |

PITR/automatic backup phụ thuộc gói Aiven. OWNER phải kiểm tra trực tiếp trạng thái và retention trong Aiven Console; không coi backup là hoạt động chỉ vì service đang chạy.

## 2. Nguyên tắc an toàn

- Không commit `.env`, database dump, certificate hoặc password.
- Mật khẩu đã xuất hiện trong chat, log hoặc ảnh phải được rotate ngay.
- Không restore đè trực tiếp lên database production. Restore sang service/database mới, xác minh rồi mới chuyển `DB_URL`.
- Không dùng production làm môi trường thử migration.
- Backup chỉ được xem là hợp lệ sau khi restore drill thành công.
- Bản ghi `audit_log` là append-only ở application layer: entity immutable và repository không cung cấp update/delete.
- Production application user chỉ được cấp `SELECT, INSERT` trên `audit_log`; quyền migration dùng credential riêng.

## 3. Kiểm tra automatic backup/PITR của Aiven

Thực hiện khi bắt đầu vận hành và kiểm tra lại mỗi tháng:

1. Mở Aiven Console và chọn MySQL service TamLottery.
2. Xác nhận service ở trạng thái `Running` và connection information hiện hành được lưu trong secret store.
3. Kiểm tra mục backup có bản gần nhất, retention và khả năng point-in-time restore.
4. Ghi ngày kiểm tra, thời điểm backup gần nhất và người kiểm tra vào nhật ký vận hành.
5. Nếu gói không hỗ trợ PITR, logical backup hằng ngày bên dưới là bắt buộc.

Không ghi password hoặc service URI đầy đủ vào nhật ký.

### Tách migration user và application user

Không chạy ứng dụng production bằng `avnadmin` lâu dài. Dùng một migration user có quyền DDL tại thời điểm deploy và một application user chỉ có DML cần thiết. Cấu hình hỗ trợ:

```env
DB_USERNAME=<application-user>
DB_PASSWORD=<application-password>
FLYWAY_DB_USERNAME=<migration-user>
FLYWAY_DB_PASSWORD=<migration-password>
```

Application user được cấp DML cho các bảng nghiệp vụ nhưng riêng `audit_log` chỉ có:

```sql
GRANT SELECT, INSERT ON defaultdb.audit_log TO '<application-user>'@'%';
```

Không cấp `UPDATE`, `DELETE`, `DROP` hoặc `ALTER` trên `audit_log`. Cách tạo user/grant phụ thuộc quyền mà Aiven service cho phép; thực hiện bằng `avnadmin` hoặc Aiven Console rồi kiểm tra bằng chính application credential. Database administrator vẫn có thể thay đổi dữ liệu nên mọi quyền quản trị phải được giới hạn và rotate riêng.

## 4. Logical backup thủ công

Yêu cầu Docker Desktop và các biến chỉ tồn tại trong terminal hiện tại:

```powershell
$env:DB_HOST='<host lấy từ Aiven Connection information>'
$env:DB_PORT='<port lấy từ Aiven>'
$env:DB_NAME='defaultdb'
$env:DB_USERNAME='avnadmin'
$env:DB_PASSWORD='<password hiện hành>'
```

Tạo dump nhất quán. Password được truyền qua environment của container, không nằm trong argument của `mysqldump`:

```powershell
$backupDir = (New-Item -ItemType Directory -Force .\backups).FullName
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = "tamlottery-$stamp.sql"

docker run --rm `
  --env "MYSQL_PWD=$env:DB_PASSWORD" `
  --mount "type=bind,source=$backupDir,target=/backup" `
  mysql:8.4 `
  mysqldump `
  "--host=$env:DB_HOST" `
  "--port=$env:DB_PORT" `
  "--user=$env:DB_USERNAME" `
  --ssl-mode=REQUIRED `
  --single-transaction `
  --routines `
  --events `
  --triggers `
  --set-gtid-purged=OFF `
  "--result-file=/backup/$backupFile" `
  --databases $env:DB_NAME

Get-Item ".\backups\$backupFile"
Get-FileHash ".\backups\$backupFile" -Algorithm SHA256 |
  Format-List | Out-File ".\backups\$backupFile.sha256"
```

Điều kiện thành công:

- `mysqldump` exit code bằng 0.
- File dump có kích thước lớn hơn 0.
- Có checksum SHA-256 đi kèm.
- File được chép sang nơi lưu trữ mã hóa, tách khỏi máy chạy ứng dụng.

Sau khi chạy xong, xóa secret khỏi terminal:

```powershell
Remove-Item Env:DB_PASSWORD
```

## 5. Restore drill vào MySQL tạm

Restore drill không kết nối tới production. Dùng container riêng với port `3307`:

```powershell
$drillPassword = [Guid]::NewGuid().ToString('N')

docker run --detach `
  --name tamlottery-restore-drill `
  --env "MYSQL_ROOT_PASSWORD=$drillPassword" `
  --publish 3307:3306 `
  mysql:8.4
```

Chờ container healthy, sau đó copy và import bản dump cần kiểm tra:

```powershell
docker inspect --format '{{.State.Health.Status}}' tamlottery-restore-drill
docker cp ".\backups\<backup-file>.sql" tamlottery-restore-drill:/tmp/backup.sql
docker exec `
  --env "MYSQL_PWD=$drillPassword" `
  tamlottery-restore-drill `
  sh -c 'exec mysql --user=root < /tmp/backup.sql'
```

Kiểm tra tối thiểu:

```powershell
docker exec `
  --env "MYSQL_PWD=$drillPassword" `
  tamlottery-restore-drill `
  mysql --user=root --database=defaultdb `
  --execute="SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank; SELECT COUNT(*) AS stores FROM store; SELECT COUNT(*) AS audits FROM audit_log;"
```

Sau đó chạy backend với DB tạm và xác nhận:

- Flyway/Hibernate schema validation pass.
- `GET /actuator/health` trả `UP`.
- Đăng nhập được bằng một tài khoản kiểm thử đã biết.
- Tổng lô, giao dịch tiền và đối soát khớp với thời điểm backup.
- Audit log vẫn đọc được; application credential không thể update/delete bản ghi audit.

Container drill là dữ liệu tạm. Sau khi ghi kết quả kiểm tra:

```powershell
docker rm --force tamlottery-restore-drill
Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
```

## 6. Khôi phục khi production gặp sự cố

1. Tuyên bố incident và ghi thời điểm phát hiện, request ID liên quan, người xử lý.
2. Chặn thao tác ghi bằng cách dừng app nếu dữ liệu tiếp tục bị sai: `docker compose stop app frontend`.
3. Không sửa trực tiếp các bảng tài chính/audit để “chữa nhanh”.
4. Chọn recovery point từ Aiven PITR hoặc logical backup gần nhất.
5. Restore sang Aiven service/database mới.
6. Chạy kiểm tra schema, số lượng bản ghi và các tổng tiền/đối soát quan trọng.
7. Rotate database credential, cập nhật `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong secret store.
8. Khởi động lại app, kiểm tra health và thực hiện smoke test.
9. Giữ database cũ ở chế độ chỉ đọc cho đến khi xác nhận hoàn tất.
10. Viết postmortem: nguyên nhân, dữ liệu ảnh hưởng, recovery point, thời gian phục hồi và hành động ngăn tái diễn.

## 7. Healthcheck và log

Kiểm tra nhanh:

```powershell
docker compose ps
$appPort = if ($env:APP_PORT) { $env:APP_PORT } else { '8080' }
Invoke-RestMethod "http://localhost:$appPort/actuator/health"
docker compose logs --since 15m app
docker compose logs --since 15m frontend
```

Backend trả header `X-Request-ID` cho mọi request. Cùng ID được ghi vào application log và audit log, dùng để nối một lỗi HTTP với thao tác nghiệp vụ cụ thể.

Docker Compose giới hạn log backend ở 5 file x 10 MB và frontend ở 3 file x 10 MB để tránh đầy đĩa. Log dài hạn cần được chuyển tới hệ thống log tập trung hoặc lưu trữ riêng.

## 8. Ma trận cảnh báo tối thiểu

| Tín hiệu | Warning | Critical | Hành động đầu tiên |
|---|---:|---:|---|
| `/actuator/health` | 1 lần fail | 2 lần liên tiếp | Kiểm tra `docker compose ps` và app logs |
| HTTP 5xx | 3 request/5 phút | 5 request/5 phút | Lọc log theo request ID |
| Container restart | 1 lần/15 phút | 2 lần/15 phút | Xem OOM, DB timeout và startup error |
| Aiven disk | trên 80% | trên 90% | Dọn retention hợp lệ/nâng storage, không xóa audit |
| Aiven connections | trên 70% limit | trên 85% limit | Kiểm tra Hikari pool và connection leak |
| Backup gần nhất | quá 24 giờ | quá 48 giờ | Chạy logical backup và điều tra scheduler |
| Reconciliation | chưa đóng cuối ngày | quá giờ mở cửa hôm sau | Kiểm tra pending cash/adjustment |

Monitor bên ngoài chỉ cần gọi endpoint health công khai. Không mở Swagger, database hoặc actuator chi tiết ra Internet.

## 9. Truy vấn vận hành

Giao dịch tiền còn pending:

```sql
SELECT id, store_id, seller_id, business_date, amount, created_at
FROM cash_transaction
WHERE status = 'PENDING'
ORDER BY created_at;
```

Ngày có dữ liệu bán nhưng chưa có đối soát store đóng:

```sql
SELECT business_date, COUNT(*) AS snapshots
FROM daily_sales
WHERE status = 'FINALIZED'
  AND NOT EXISTS (
      SELECT 1
      FROM daily_reconciliation r
      WHERE r.store_id = daily_sales.store_id
        AND r.business_date = daily_sales.business_date
        AND r.scope = 'STORE'
        AND r.status = 'CLOSED'
  )
GROUP BY business_date;
```

Audit gần nhất của một aggregate:

```sql
SELECT occurred_at, actor_username, actor_roles, action, reason, request_id
FROM audit_log
WHERE store_id = ?
  AND entity_type = ?
  AND entity_id = ?
ORDER BY occurred_at DESC;
```

Ứng dụng nên dùng `GET /api/v1/audit-logs` thay vì cấp quyền SQL trực tiếp cho người dùng nghiệp vụ.

## 10. Checklist định kỳ

Hằng ngày:

- Healthcheck đang `UP`.
- Không có container restart bất thường.
- Không còn cash/adjustment pending ngoài dự kiến.
- Đối soát ngày đã đóng hoặc có lý do xử lý tiếp.

Hằng tuần:

- Kiểm tra dung lượng Aiven và log host.
- Xác minh backup mới nhất và checksum logical backup.
- Rà các action `VOIDED`, `REJECTED`, `CANCELLED` trong audit log.

Hằng tháng:

- Restore drill một backup ngẫu nhiên.
- Rotate credential nếu có nghi ngờ lộ secret.
- Rà quyền OWNER/MANAGER/SELLER và vô hiệu hóa tài khoản không còn sử dụng.
- Cập nhật runbook sau incident hoặc thay đổi hạ tầng.
