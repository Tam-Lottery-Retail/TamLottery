# Tâm Lottery Frontend

Giao diện vận hành cửa hàng viết bằng React 19, TypeScript và Vinext. Frontend gọi backend thông qua route proxy cùng origin nên không cần mở CORS.

## Chạy local

Backend cần chạy tại `http://localhost:8080`.

```powershell
npm install
npm run dev
```

Mở URL được in trong terminal, mặc định là `http://localhost:3000`.

Nếu backend chạy ở địa chỉ khác:

```powershell
$env:BACKEND_URL='http://localhost:8080'
npm run dev
```

## Nhập lô vé

Mở **Nhận vé → Nhận lô vé** rồi chọn một trong hai cách:

1. **Nhập thủ công**: điền thông tin đài/ngày quay ngay trên từng dòng vé.
2. **Tải file CSV/XLSX**: tải [file mẫu](public/templates/lo-ve-mau.csv), đưa file lên để trích xuất,
   kiểm tra preview rồi mới lưu.

Upload chỉ phục vụ parse tạm thời, file gốc không được lưu. MVP chưa hỗ trợ OCR ảnh/PDF.

## Kiểm tra

```powershell
npm test
```

## Docker

Chạy từ thư mục gốc của repository:

```powershell
docker compose up -d --build
```

Frontend mặc định ở `http://localhost:3000`, backend ở `http://localhost:8080`.
