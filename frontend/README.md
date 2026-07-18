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
