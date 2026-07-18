"use client";

import { FormEvent, ReactNode, useCallback, useEffect, useState } from "react";

type Role = "OWNER" | "MANAGER" | "SELLER";
type Section =
  | "overview"
  | "batches"
  | "allocations"
  | "returns"
  | "cash"
  | "reconciliation"
  | "catalog"
  | "team";
type ModalKind =
  | "agency"
  | "user"
  | "seller"
  | "batch"
  | "allocation"
  | "return"
  | "adjustment"
  | "cash";

type PageResponse<T> = { content: T[]; totalElements: number };
type TokenResponse = { tokenType: string; accessToken: string; refreshToken: string; expiresInSeconds: number };
type Store = { id: number; code: string; name: string; timezone: string; status: string };
type Agency = { id: number; code: string; name: string; contactName?: string; phone?: string; active: boolean };
type Draw = { id: number; issuerName: string; provinceCode: string; region: string; drawDate: string; returnCutoffAt: string; status: string };
type User = { id: number; username: string; fullName: string; status: string; roles: Role[] };
type Seller = { id: number; userId?: number; code: string; fullName: string; phone?: string; status: string };
type BatchLine = { id: number; drawId: number; issuerName: string; provinceCode: string; region: string; drawDate: string; returnCutoffAt: string; quantityReceived: number; unitCost: number; unitSalePrice: number; serialFrom?: string; serialTo?: string };
type BatchLineDraft = { key: number; issuerName: string; provinceCode: string; region: string; drawDate: string; returnCutoffAt: string; quantityReceived: string; unitCost: string; unitSalePrice: string; serialFrom: string; serialTo: string };
type BatchImportPreview = { fileName: string; agencyCode?: string; receiptCode?: string; businessDate?: string; receivedAt?: string; note?: string; lines: Array<Omit<BatchLineDraft, "key" | "quantityReceived" | "unitCost" | "unitSalePrice"> & { quantityReceived: number; unitCost: number; unitSalePrice: number }>; warnings: string[] };
type Batch = { id: number; agencyId: number; agencyName: string; receiptCode: string; businessDate: string; receivedAt: string; status: string; note?: string; lines: BatchLine[] };
type AllocationLine = { id: number; batchLineId: number; provinceCode: string; drawDate: string; quantity: number };
type Allocation = { id: number; sellerId: number; sellerName: string; businessDate: string; issuedAt?: string; status: string; note?: string; lines: AllocationLine[] };
type ReturnLine = { id: number; batchLineId: number; allocationLineId?: number; quantity: number };
type TicketReturn = { id: number; returnType: string; sellerId?: number; agencyId?: number; businessDate: string; returnedAt?: string; status: string; note?: string; lines: ReturnLine[] };
type Adjustment = { id: number; batchLineId: number; holderType: string; sellerId?: number; allocationLineId?: number; adjustmentType: string; direction: string; quantity: number; reason: string; status: string };
type CashTransaction = { id: number; sellerId?: number; reconciliationId?: number; businessDate: string; direction: string; transactionType: string; paymentMethod: string; amount: number; occurredAt: string; note?: string; status: string; postedAt?: string };
type SalesLine = { batchLineId: number; provinceCode: string; drawDate: string; baseQuantity: number; returnedQuantity: number; lostQuantity: number; soldQuantity: number; unitSalePrice: number; expectedAmount: number };
type Preview = { businessDate: string; scope: string; sellerId?: number; totalBaseQuantity: number; totalReturnedQuantity: number; totalLostQuantity: number; totalSoldQuantity: number; expectedAmount: number; actualReceivedAmount: number; differenceAmount: number; lines: SalesLine[] };
type Reconciliation = { id: number; dailySalesId: number; businessDate: string; scope: string; sellerId?: number; revision: number; expectedAmount: number; actualReceivedAmount: number; differenceAmount: number; status: string; note?: string; closedAt?: string };

type Snapshot = {
  store?: Store;
  agencies: Agency[];
  draws: Draw[];
  users: User[];
  sellers: Seller[];
  batches: Batch[];
  allocations: Allocation[];
  returns: TicketReturn[];
  adjustments: Adjustment[];
  cash: CashTransaction[];
  reconciliations: Reconciliation[];
  preview?: Preview;
};

const EMPTY_SNAPSHOT: Snapshot = {
  agencies: [], draws: [], users: [], sellers: [], batches: [], allocations: [],
  returns: [], adjustments: [], cash: [], reconciliations: [],
};

const today = () => new Date().toISOString().slice(0, 10);
const nowLocal = () => {
  const value = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000);
  return value.toISOString().slice(0, 16);
};
const toLocalDateTime = (value?: string) => {
  if (!value) return nowLocal();
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
};
const emptyBatchLine = (key = Date.now()): BatchLineDraft => ({
  key, issuerName: "", provinceCode: "", region: "SOUTH", drawDate: today(), returnCutoffAt: nowLocal(),
  quantityReceived: "100", unitCost: "9000", unitSalePrice: "10000", serialFrom: "", serialTo: "",
});
const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 });
const number = new Intl.NumberFormat("vi-VN");
const formatMoney = (value?: number) => money.format(value ?? 0);
const formatDate = (value?: string) => value ? new Intl.DateTimeFormat("vi-VN").format(new Date(`${value.slice(0, 10)}T00:00:00`)) : "—";
const formatDateTime = (value?: string) => value ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—";
const pageContent = <T,>(value: PageResponse<T> | T[] | undefined): T[] => Array.isArray(value) ? value : value?.content ?? [];
const qs = "?size=100&sort=id,desc";

const STATUS_LABELS: Record<string, string> = {
  DRAFT: "Bản nháp", CONFIRMED: "Đã xác nhận", CLOSED: "Đã chốt", CANCELLED: "Đã hủy",
  ISSUED: "Đã giao", RECONCILED: "Đã đối soát", PENDING: "Chờ xác nhận", POSTED: "Đã ghi sổ",
  VOIDED: "Đã hủy", APPROVED: "Đã duyệt", REJECTED: "Từ chối", REVIEW_REQUIRED: "Cần xem xét",
  ACTIVE: "Hoạt động", INACTIVE: "Ngừng hoạt động", LOCKED: "Đã khóa", DISABLED: "Đã vô hiệu",
  OPEN: "Đang mở", SELLER_TO_STORE: "Seller trả cửa hàng", STORE_TO_AGENCY: "Cửa hàng trả đại lý",
};

class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

function saveTokens(tokens: TokenResponse) {
  sessionStorage.setItem("tamlottery.access", tokens.accessToken);
  sessionStorage.setItem("tamlottery.refresh", tokens.refreshToken);
}

function clearTokens() {
  sessionStorage.removeItem("tamlottery.access");
  sessionStorage.removeItem("tamlottery.refresh");
}

async function api<T>(path: string, init: RequestInit = {}, canRefresh = true): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  const accessToken = sessionStorage.getItem("tamlottery.access");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const response = await fetch(path, { ...init, headers, cache: "no-store" });
  if (response.status === 401 && canRefresh && !path.includes("/auth/")) {
    const refreshToken = sessionStorage.getItem("tamlottery.refresh");
    if (refreshToken) {
      const refreshed = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (refreshed.ok) {
        saveTokens(await refreshed.json());
        return api<T>(path, init, false);
      }
    }
    clearTokens();
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const details = Array.isArray(problem.violations)
      ? problem.violations.map((item: { field?: string; message?: string }) => `${item.field}: ${item.message}`).join(", ")
      : undefined;
    throw new ApiError(response.status, details || problem.detail || problem.title || `Yêu cầu thất bại (${response.status})`);
  }
  if (response.status === 204) return undefined as T;
  return response.json();
}

function decodeRoles(token: string | null): Role[] {
  if (!token) return [];
  try {
    const part = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(part.padEnd(Math.ceil(part.length / 4) * 4, "=")));
    return Array.isArray(payload.roles) ? payload.roles : [];
  } catch { return []; }
}

const NAV_ITEMS: Array<{ id: Section; icon: string; label: string; managerOnly?: boolean }> = [
  { id: "overview", icon: "01", label: "Tổng quan" },
  { id: "batches", icon: "02", label: "Nhận vé", managerOnly: true },
  { id: "allocations", icon: "03", label: "Giao vé", managerOnly: true },
  { id: "returns", icon: "04", label: "Trả & thất thoát" },
  { id: "cash", icon: "05", label: "Giao dịch tiền" },
  { id: "reconciliation", icon: "06", label: "Đối soát" },
  { id: "catalog", icon: "07", label: "Đại lý & kỳ vé", managerOnly: true },
  { id: "team", icon: "08", label: "Nhân sự", managerOnly: true },
];

export default function Home() {
  const [authenticated, setAuthenticated] = useState(false);
  const [roles, setRoles] = useState<Role[]>([]);
  const [active, setActive] = useState<Section>("overview");
  const [snapshot, setSnapshot] = useState<Snapshot>(EMPTY_SNAPSHOT);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState<ModalKind | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "error"; message: string } | null>(null);

  const isManager = roles.includes("OWNER") || roles.includes("MANAGER");
  const isOwner = roles.includes("OWNER");

  const load = useCallback(async () => {
    setLoading(true);
    const safe = async <T,>(path: string, fallback: T): Promise<T> => {
      try { return await api<T>(path); }
      catch (error) {
        if (error instanceof ApiError && error.status === 403) return fallback;
        throw error;
      }
    };
    try {
      const [store, agencies, draws, users, sellers, batches, allocations, returns, adjustments, cash, reconciliations, preview] = await Promise.all([
        api<Store>("/api/v1/stores/current"),
        isManager ? safe<PageResponse<Agency>>(`/api/v1/agencies${qs}`, { content: [], totalElements: 0 }) : Promise.resolve({ content: [], totalElements: 0 }),
        safe<PageResponse<Draw>>(`/api/v1/draws${qs}`, { content: [], totalElements: 0 }),
        isOwner ? safe<PageResponse<User>>(`/api/v1/users${qs}`, { content: [], totalElements: 0 }) : Promise.resolve({ content: [], totalElements: 0 }),
        isManager ? safe<PageResponse<Seller>>(`/api/v1/sellers${qs}`, { content: [], totalElements: 0 }) : Promise.resolve({ content: [], totalElements: 0 }),
        isManager ? safe<PageResponse<Batch>>(`/api/v1/batches${qs}`, { content: [], totalElements: 0 }) : Promise.resolve({ content: [], totalElements: 0 }),
        isManager ? safe<PageResponse<Allocation>>(`/api/v1/allocations${qs}`, { content: [], totalElements: 0 }) : Promise.resolve({ content: [], totalElements: 0 }),
        isManager ? safe<PageResponse<TicketReturn>>(`/api/v1/returns${qs}`, { content: [], totalElements: 0 }) : Promise.resolve({ content: [], totalElements: 0 }),
        isManager ? safe<PageResponse<Adjustment>>(`/api/v1/inventory-adjustments${qs}`, { content: [], totalElements: 0 }) : Promise.resolve({ content: [], totalElements: 0 }),
        safe<PageResponse<CashTransaction>>(`/api/v1/cash-transactions${qs}`, { content: [], totalElements: 0 }),
        safe<PageResponse<Reconciliation>>(`/api/v1/reconciliations${qs}`, { content: [], totalElements: 0 }),
        safe<Preview>(`/api/v1/reconciliations/preview?businessDate=${today()}&scope=STORE`, undefined as unknown as Preview),
      ]);
      setSnapshot({ store, agencies: pageContent(agencies), draws: pageContent(draws), users: pageContent(users), sellers: pageContent(sellers), batches: pageContent(batches), allocations: pageContent(allocations), returns: pageContent(returns), adjustments: pageContent(adjustments), cash: pageContent(cash), reconciliations: pageContent(reconciliations), preview });
      setAuthenticated(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearTokens();
        setAuthenticated(false);
      } else {
        setNotice({ tone: "error", message: error instanceof Error ? error.message : "Không thể tải dữ liệu" });
      }
    } finally { setLoading(false); }
  }, [isManager, isOwner]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const token = sessionStorage.getItem("tamlottery.access");
      setRoles(decodeRoles(token));
      if (token) setAuthenticated(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (!authenticated || !roles.length) return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [authenticated, roles, load]);

  const notify = (tone: "success" | "error", message: string) => {
    setNotice({ tone, message });
    window.setTimeout(() => setNotice(null), 4200);
  };

  const perform = async (path: string, success: string, method = "POST", body?: unknown) => {
    try {
      await api(path, { method, body: body === undefined ? undefined : JSON.stringify(body) });
      notify("success", success);
      await load();
    } catch (error) { notify("error", error instanceof Error ? error.message : "Có lỗi xảy ra"); }
  };

  const logout = async () => {
    const refreshToken = sessionStorage.getItem("tamlottery.refresh");
    if (refreshToken) await api("/api/v1/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }) }).catch(() => undefined);
    clearTokens();
    setAuthenticated(false);
    setRoles([]);
    setSnapshot(EMPTY_SNAPSHOT);
  };

  if (!authenticated) return <LoginScreen onLogin={(newRoles) => { setRoles(newRoles); setAuthenticated(true); }} />;

  const visibleNav = NAV_ITEMS.filter((item) => !item.managerOnly || isManager);
  const title = visibleNav.find((item) => item.id === active)?.label ?? "Tổng quan";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">T</span><div><strong>Tâm Lottery</strong><small>Quản lý cửa hàng</small></div></div>
        <nav aria-label="Điều hướng chính">
          {visibleNav.map((item) => (
            <button key={item.id} className={active === item.id ? "nav-item active" : "nav-item"} onClick={() => setActive(item.id)}>
              <span>{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="store-chip"><span>{snapshot.store?.code?.slice(0, 2) ?? "TL"}</span><div><strong>{snapshot.store?.name ?? "Tâm Lottery"}</strong><small>{roles.join(" · ")}</small></div></div>
          <button className="text-button" onClick={logout}>Đăng xuất</button>
        </div>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div><p className="eyebrow">{formatDate(today())}</p><h1>{title}</h1></div>
          <div className="topbar-actions">
            <button className="icon-button" aria-label="Làm mới" onClick={() => void load()} disabled={loading}>↻</button>
            <QuickCreate active={active} isManager={isManager} onOpen={setModal} />
          </div>
        </header>

        {loading && <div className="loading-bar" aria-label="Đang tải" />}
        <section className="content-area">
          {active === "overview" && <Overview snapshot={snapshot} onNavigate={setActive} />}
          {active === "batches" && <BatchesView items={snapshot.batches} onAction={perform} />}
          {active === "allocations" && <AllocationsView items={snapshot.allocations} onAction={perform} />}
          {active === "returns" && <ReturnsView returns={snapshot.returns} adjustments={snapshot.adjustments} isManager={isManager} onOpen={setModal} onAction={perform} />}
          {active === "cash" && <CashView items={snapshot.cash} isManager={isManager} onAction={perform} />}
          {active === "reconciliation" && <ReconciliationView items={snapshot.reconciliations} sellers={snapshot.sellers} isOwner={isOwner} onAction={perform} notify={notify} reload={load} />}
          {active === "catalog" && <CatalogView agencies={snapshot.agencies} draws={snapshot.draws} />}
          {active === "team" && <TeamView users={snapshot.users} sellers={snapshot.sellers} isOwner={isOwner} onOpen={setModal} />}
        </section>
      </main>

      {modal && <EntityModal kind={modal} snapshot={snapshot} onClose={() => setModal(null)} onCreated={async (message) => { setModal(null); notify("success", message); await load(); }} />}
      {notice && <div className={`toast ${notice.tone}`} role="status"><span>{notice.tone === "success" ? "✓" : "!"}</span>{notice.message}</div>}
    </div>
  );
}

function LoginScreen({ onLogin }: { onLogin: (roles: Role[]) => void }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setLoading(true); setError("");
    const data = new FormData(event.currentTarget);
    try {
      const tokens = await api<TokenResponse>("/api/v1/auth/login", { method: "POST", body: JSON.stringify({ username: data.get("username"), password: data.get("password") }) });
      saveTokens(tokens); onLogin(decodeRoles(tokens.accessToken));
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Đăng nhập thất bại"); }
    finally { setLoading(false); }
  };
  return (
    <main className="login-page">
      <section className="login-story">
        <div className="login-brand"><span className="brand-mark light">T</span><strong>Tâm Lottery</strong></div>
        <div><p className="eyebrow light-text">Vận hành rõ ràng mỗi ngày</p><h1>Quản lý vé, tiền và đối soát trong một nhịp làm việc.</h1><p>Theo dõi từ lúc nhận vé đến khi chốt ngày, không còn cộng trừ thủ công trên nhiều sổ.</p></div>
        <div className="story-metrics"><span><strong>01</strong>Nhận và giao vé</span><span><strong>02</strong>Trả và thất thoát</span><span><strong>03</strong>Thu tiền, chốt ngày</span></div>
      </section>
      <section className="login-panel">
        <form className="login-card" onSubmit={submit}>
          <div><p className="eyebrow">Xin chào</p><h2>Đăng nhập cửa hàng</h2><p className="muted">Dùng tài khoản owner, quản lý hoặc người bán.</p></div>
          <label>Tên đăng nhập<input name="username" autoComplete="username" placeholder="owner" required autoFocus /></label>
          <label>Mật khẩu<input type="password" name="password" autoComplete="current-password" placeholder="••••••••" required /></label>
          {error && <p className="form-error">{error}</p>}
          <button className="primary-button wide" disabled={loading}>{loading ? "Đang đăng nhập…" : "Đăng nhập"}</button>
          <small className="secure-note">Phiên đăng nhập được bảo vệ bằng access token và tự làm mới an toàn.</small>
        </form>
      </section>
    </main>
  );
}

function QuickCreate({ active, isManager, onOpen }: { active: Section; isManager: boolean; onOpen: (kind: ModalKind) => void }) {
  const map: Partial<Record<Section, { kind: ModalKind; label: string }>> = {
    batches: { kind: "batch", label: "Nhận lô vé" }, allocations: { kind: "allocation", label: "Giao vé" },
    returns: { kind: "return", label: "Tạo phiếu trả" }, cash: { kind: "cash", label: "Thêm giao dịch" },
    catalog: { kind: "agency", label: "Thêm đại lý" }, team: { kind: "seller", label: "Thêm seller" },
  };
  const action = map[active] ?? (isManager ? { kind: "batch" as ModalKind, label: "Nhập liệu mới" } : { kind: "cash" as ModalKind, label: "Giao tiền" });
  return <button className="primary-button" onClick={() => onOpen(action.kind)}>＋ {action.label}</button>;
}

function Overview({ snapshot, onNavigate }: { snapshot: Snapshot; onNavigate: (section: Section) => void }) {
  const date = today();
  const todaysBatches = snapshot.batches.filter((item) => item.businessDate === date);
  const received = todaysBatches.flatMap((item) => item.lines).reduce((sum, item) => sum + item.quantityReceived, 0);
  const allocated = snapshot.allocations.filter((item) => item.businessDate === date && item.status === "ISSUED").flatMap((item) => item.lines).reduce((sum, item) => sum + item.quantity, 0);
  const pending = snapshot.adjustments.filter((item) => item.status === "PENDING").length + snapshot.cash.filter((item) => item.status === "PENDING").length;
  const preview = snapshot.preview;
  const recent = [
    ...snapshot.batches.slice(0, 3).map((item) => ({ time: item.receivedAt, title: `Nhận lô ${item.receiptCode}`, meta: `${item.agencyName} · ${number.format(item.lines.reduce((s, l) => s + l.quantityReceived, 0))} vé`, status: item.status })),
    ...snapshot.cash.slice(0, 3).map((item) => ({ time: item.occurredAt, title: item.direction === "IN" ? "Nhận tiền" : "Chi tiền", meta: `${formatMoney(item.amount)} · ${item.paymentMethod}`, status: item.status })),
  ].sort((a, b) => (b.time ?? "").localeCompare(a.time ?? "")).slice(0, 5);
  return (
    <div className="stack-lg">
      <section className="hero-card">
        <div><p className="eyebrow light-text">Cửa hàng hôm nay</p><h2>{preview?.differenceAmount === 0 ? "Sổ sách đang cân bằng." : "Có chênh lệch cần kiểm tra."}</h2><p>{preview ? `${number.format(preview.totalSoldQuantity)} vé đã bán, dự kiến thu ${formatMoney(preview.expectedAmount)}.` : "Nhập lô vé đầu tiên để bắt đầu theo dõi doanh thu."}</p></div>
        <div className="hero-total"><small>Chênh lệch hiện tại</small><strong>{formatMoney(preview?.differenceAmount)}</strong><span className={preview?.differenceAmount === 0 ? "signal good" : "signal warn"}>{preview?.differenceAmount === 0 ? "Đã khớp" : "Cần đối soát"}</span></div>
      </section>
      <section className="metric-grid">
        <Metric label="Vé nhận" value={number.format(received)} hint={`${todaysBatches.length} lô trong ngày`} tone="ink" />
        <Metric label="Vé đã giao" value={number.format(allocated)} hint={`${snapshot.allocations.filter((item) => item.businessDate === date).length} lượt giao`} tone="blue" />
        <Metric label="Doanh thu dự kiến" value={formatMoney(preview?.expectedAmount)} hint={`${number.format(preview?.totalSoldQuantity ?? 0)} vé bán`} tone="green" />
        <Metric label="Việc chờ xử lý" value={number.format(pending)} hint="Tiền và điều chỉnh tồn" tone="orange" />
      </section>
      <section className="two-column">
        <div className="panel">
          <PanelHeader title="Thao tác nhanh" subtitle="Các việc thường dùng trong ngày" />
          <div className="quick-grid">
            <button onClick={() => onNavigate("batches")}><span>NV</span><strong>Nhận vé</strong><small>Tạo và xác nhận lô</small></button>
            <button onClick={() => onNavigate("allocations")}><span>GV</span><strong>Giao vé</strong><small>Phân bổ cho seller</small></button>
            <button onClick={() => onNavigate("returns")}><span>TV</span><strong>Trả vé</strong><small>Nhập trả hoặc mất</small></button>
            <button onClick={() => onNavigate("reconciliation")}><span>ĐS</span><strong>Đối soát</strong><small>Kiểm tra và chốt ngày</small></button>
          </div>
        </div>
        <div className="panel">
          <PanelHeader title="Hoạt động gần đây" subtitle="Cập nhật mới nhất của cửa hàng" />
          <div className="activity-list">
            {recent.length ? recent.map((item, index) => <div className="activity" key={`${item.title}-${index}`}><span className="activity-dot" /><div><strong>{item.title}</strong><small>{item.meta}</small></div><div><Status value={item.status} /><small>{formatDateTime(item.time)}</small></div></div>) : <Empty text="Chưa có hoạt động trong ngày" />}
          </div>
        </div>
      </section>
    </div>
  );
}

function BatchesView({ items, onAction }: { items: Batch[]; onAction: (path: string, success: string) => Promise<void> }) {
  return <div className="panel"><PanelHeader title="Lô vé đã nhận" subtitle="Theo dõi phiếu nhận vé từ đại lý" count={items.length} />
    <DataTable headers={["Phiếu nhận", "Ngày bán", "Đại lý", "Số vé", "Giá trị bán", "Trạng thái", ""]} empty="Chưa có lô vé nào">
      {items.map((item) => <tr key={item.id}><td><strong>{item.receiptCode}</strong><small>#{item.id} · {formatDateTime(item.receivedAt)}</small></td><td>{formatDate(item.businessDate)}</td><td>{item.agencyName}</td><td>{number.format(item.lines.reduce((s, l) => s + l.quantityReceived, 0))}</td><td>{formatMoney(item.lines.reduce((s, l) => s + l.quantityReceived * l.unitSalePrice, 0))}</td><td><Status value={item.status} /></td><td>{item.status === "DRAFT" && <button className="table-action" onClick={() => onAction(`/api/v1/batches/${item.id}/confirm`, "Đã xác nhận lô vé")}>Xác nhận</button>}</td></tr>)}
    </DataTable></div>;
}

function AllocationsView({ items, onAction }: { items: Allocation[]; onAction: (path: string, success: string) => Promise<void> }) {
  return <div className="panel"><PanelHeader title="Lượt giao vé" subtitle="Phân bổ tồn kho từ cửa hàng cho người bán" count={items.length} />
    <DataTable headers={["Seller", "Ngày bán", "Số vé giao", "Thời điểm giao", "Trạng thái", ""]} empty="Chưa có lượt giao vé">
      {items.map((item) => <tr key={item.id}><td><strong>{item.sellerName}</strong><small>Phiếu giao #{item.id}</small></td><td>{formatDate(item.businessDate)}</td><td>{number.format(item.lines.reduce((s, l) => s + l.quantity, 0))}</td><td>{formatDateTime(item.issuedAt)}</td><td><Status value={item.status} /></td><td>{item.status === "DRAFT" && <button className="table-action" onClick={() => onAction(`/api/v1/allocations/${item.id}/issue`, "Đã giao vé cho seller")}>Giao vé</button>}</td></tr>)}
    </DataTable></div>;
}

function ReturnsView({ returns, adjustments, isManager, onOpen, onAction }: { returns: TicketReturn[]; adjustments: Adjustment[]; isManager: boolean; onOpen: (kind: ModalKind) => void; onAction: (path: string, success: string) => Promise<void> }) {
  return <div className="stack-lg"><div className="section-actions"><button className="secondary-button" onClick={() => onOpen("adjustment")}>＋ Báo thất thoát</button><button className="primary-button" onClick={() => onOpen("return")}>＋ Tạo phiếu trả</button></div>
    <div className="panel"><PanelHeader title="Phiếu trả vé" subtitle="Phân biệt seller trả cửa hàng và cửa hàng trả đại lý" count={returns.length} /><DataTable headers={["Loại phiếu", "Ngày", "Số lượng", "Đối tượng", "Trạng thái", ""]} empty="Chưa có phiếu trả vé">
      {returns.map((item) => <tr key={item.id}><td><strong>{STATUS_LABELS[item.returnType]}</strong><small>Phiếu #{item.id}</small></td><td>{formatDate(item.businessDate)}</td><td>{number.format(item.lines.reduce((s, l) => s + l.quantity, 0))}</td><td>{item.sellerId ? `Seller #${item.sellerId}` : `Đại lý #${item.agencyId}`}</td><td><Status value={item.status} /></td><td>{isManager && item.status === "DRAFT" && <button className="table-action" onClick={() => onAction(`/api/v1/returns/${item.id}/confirm`, "Đã xác nhận phiếu trả")}>Xác nhận</button>}</td></tr>)}
    </DataTable></div>
    <div className="panel"><PanelHeader title="Điều chỉnh tồn kho" subtitle="Thất thoát, hư hỏng và điều chỉnh số lượng" count={adjustments.length} /><DataTable headers={["Loại", "Vị trí", "Số lượng", "Lý do", "Trạng thái", ""]} empty="Chưa có điều chỉnh tồn kho">
      {adjustments.map((item) => <tr key={item.id}><td><strong>{item.adjustmentType}</strong><small>{item.direction}</small></td><td>{item.holderType}{item.sellerId ? ` #${item.sellerId}` : ""}</td><td>{number.format(item.quantity)}</td><td>{item.reason}</td><td><Status value={item.status} /></td><td>{isManager && item.status === "PENDING" && <button className="table-action" onClick={() => onAction(`/api/v1/inventory-adjustments/${item.id}/approve`, "Đã duyệt điều chỉnh")}>Duyệt</button>}</td></tr>)}
    </DataTable></div></div>;
}

function CashView({ items, isManager, onAction }: { items: CashTransaction[]; isManager: boolean; onAction: (path: string, success: string) => Promise<void> }) {
  const totalIn = items.filter((item) => item.status === "POSTED" && item.direction === "IN").reduce((s, i) => s + i.amount, 0);
  const totalOut = items.filter((item) => item.status === "POSTED" && item.direction === "OUT").reduce((s, i) => s + i.amount, 0);
  return <div className="stack-lg"><section className="mini-metrics"><Metric label="Tổng thu đã ghi sổ" value={formatMoney(totalIn)} hint="Giao dịch IN" tone="green" /><Metric label="Tổng chi đã ghi sổ" value={formatMoney(totalOut)} hint="Giao dịch OUT" tone="orange" /><Metric label="Tiền ròng" value={formatMoney(totalIn - totalOut)} hint="Thu trừ chi" tone="ink" /></section>
    <div className="panel"><PanelHeader title="Giao dịch tiền" subtitle="Mỗi giao dịch có thể được gom vào một lần đối soát" count={items.length} /><DataTable headers={["Giao dịch", "Ngày", "Seller", "Số tiền", "Phương thức", "Trạng thái", ""]} empty="Chưa có giao dịch tiền">
      {items.map((item) => <tr key={item.id}><td><strong>{item.transactionType}</strong><small>#{item.id} · {formatDateTime(item.occurredAt)}</small></td><td>{formatDate(item.businessDate)}</td><td>{item.sellerId ? `#${item.sellerId}` : "Tại quầy"}</td><td className={item.direction === "IN" ? "positive" : "negative"}>{item.direction === "IN" ? "+" : "−"}{formatMoney(item.amount)}</td><td>{item.paymentMethod}</td><td><Status value={item.status} /></td><td>{isManager && item.status === "PENDING" && <button className="table-action" onClick={() => onAction(`/api/v1/cash-transactions/${item.id}/post`, "Đã ghi sổ giao dịch")}>Ghi sổ</button>}</td></tr>)}
    </DataTable></div></div>;
}

function ReconciliationView({ items, sellers, isOwner, onAction, notify, reload }: { items: Reconciliation[]; sellers: Seller[]; isOwner: boolean; onAction: (path: string, success: string) => Promise<void>; notify: (tone: "success" | "error", message: string) => void; reload: () => Promise<void> }) {
  const [date, setDate] = useState(today()); const [scope, setScope] = useState("STORE"); const [sellerId, setSellerId] = useState(""); const [preview, setPreview] = useState<Preview>(); const [loading, setLoading] = useState(false);
  const loadPreview = async () => { setLoading(true); try { setPreview(await api<Preview>(`/api/v1/reconciliations/preview?businessDate=${date}&scope=${scope}${scope === "SELLER" ? `&sellerId=${sellerId}` : ""}`)); } catch (error) { notify("error", error instanceof Error ? error.message : "Không thể xem trước"); } finally { setLoading(false); } };
  const close = async () => { try { await api("/api/v1/reconciliations/close", { method: "POST", body: JSON.stringify({ businessDate: date, scope, sellerId: scope === "SELLER" ? Number(sellerId) : null, note: preview?.differenceAmount ? "Chênh lệch cần kiểm tra" : null }) }); notify("success", "Đã tạo đối soát và snapshot doanh thu"); await reload(); } catch (error) { notify("error", error instanceof Error ? error.message : "Không thể chốt đối soát"); } };
  return <div className="stack-lg"><div className="panel reconciliation-builder"><PanelHeader title="Đối soát trong ngày" subtitle="Xem trước số vé, tiền dự kiến và tiền thực nhận trước khi chốt" /><div className="filter-row"><label>Ngày bán<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label><label>Phạm vi<select value={scope} onChange={(e) => setScope(e.target.value)}><option value="STORE">Toàn cửa hàng</option><option value="SELLER">Theo seller</option></select></label>{scope === "SELLER" && <label>Seller<select value={sellerId} onChange={(e) => setSellerId(e.target.value)} required><option value="">Chọn seller</option>{sellers.map((seller) => <option key={seller.id} value={seller.id}>{seller.fullName}</option>)}</select></label>}<button className="secondary-button" onClick={loadPreview} disabled={loading || (scope === "SELLER" && !sellerId)}>{loading ? "Đang tính…" : "Xem trước"}</button></div>
      {preview && <div className="preview-box"><div><small>Vé bán</small><strong>{number.format(preview.totalSoldQuantity)}</strong></div><div><small>Tiền dự kiến</small><strong>{formatMoney(preview.expectedAmount)}</strong></div><div><small>Tiền thực nhận</small><strong>{formatMoney(preview.actualReceivedAmount)}</strong></div><div><small>Chênh lệch</small><strong className={preview.differenceAmount === 0 ? "positive" : "negative"}>{formatMoney(preview.differenceAmount)}</strong></div><button className="primary-button" onClick={close}>Chốt đối soát</button></div>}
    </div>
    <div className="panel"><PanelHeader title="Lịch sử đối soát" subtitle="Snapshot không thể chỉnh sửa trực tiếp sau khi chốt" count={items.length} /><DataTable headers={["Ngày", "Phạm vi", "Dự kiến", "Thực nhận", "Chênh lệch", "Trạng thái", ""]} empty="Chưa có lần đối soát">
      {items.map((item) => <tr key={item.id}><td><strong>{formatDate(item.businessDate)}</strong><small>Lần #{item.revision}</small></td><td>{item.scope}{item.sellerId ? ` #${item.sellerId}` : ""}</td><td>{formatMoney(item.expectedAmount)}</td><td>{formatMoney(item.actualReceivedAmount)}</td><td className={item.differenceAmount === 0 ? "positive" : "negative"}>{formatMoney(item.differenceAmount)}</td><td><Status value={item.status} /></td><td>{isOwner && item.status === "REVIEW_REQUIRED" && <button className="table-action" onClick={() => onAction(`/api/v1/reconciliations/${item.id}/approve`, "Đã duyệt đối soát")}>Duyệt</button>}</td></tr>)}
    </DataTable></div></div>;
}

function CatalogView({ agencies, draws }: { agencies: Agency[]; draws: Draw[] }) {
  return <div className="stack-lg"><section className="two-column equal"><div className="panel"><PanelHeader title="Đại lý cấp 1" subtitle="Nguồn nhận và nơi trả vé" count={agencies.length} /><div className="card-list">{agencies.length ? agencies.map((agency) => <article className="entity-card" key={agency.id}><div className="entity-avatar">{agency.code.slice(0, 2)}</div><div><strong>{agency.name}</strong><small>{agency.contactName || "Chưa có người liên hệ"} · {agency.phone || "Chưa có SĐT"}</small></div><Status value={agency.active ? "ACTIVE" : "INACTIVE"} /></article>) : <Empty text="Chưa có đại lý" />}</div></div>
    <div className="panel"><PanelHeader title="Kỳ vé đã ghi nhận" subtitle="Hệ thống tự tạo khi cửa hàng nhận lô vé từ đại lý" count={draws.length} /><div className="card-list">{draws.length ? draws.map((draw) => <article className="entity-card" key={draw.id}><div className="date-tile"><strong>{draw.drawDate.slice(8, 10)}</strong><small>Th.{draw.drawDate.slice(5, 7)}</small></div><div><strong>{draw.issuerName}</strong><small>{draw.provinceCode} · {draw.region} · Hạn trả {formatDateTime(draw.returnCutoffAt)}</small></div><Status value={draw.status} /></article>) : <Empty text="Kỳ vé sẽ xuất hiện sau khi nhận lô đầu tiên" />}</div></div></section></div>;
}

function TeamView({ users, sellers, isOwner, onOpen }: { users: User[]; sellers: Seller[]; isOwner: boolean; onOpen: (kind: ModalKind) => void }) {
  return <div className="stack-lg"><div className="section-actions">{isOwner && <button className="secondary-button" onClick={() => onOpen("user")}>＋ Tạo tài khoản</button>}<button className="primary-button" onClick={() => onOpen("seller")}>＋ Thêm seller</button></div><section className="two-column equal">{isOwner && <div className="panel"><PanelHeader title="Tài khoản" subtitle="Phân quyền owner, manager và seller" count={users.length} /><div className="card-list">{users.length ? users.map((user) => <article className="entity-card" key={user.id}><div className="entity-avatar pale">{user.fullName.slice(0, 2).toUpperCase()}</div><div><strong>{user.fullName}</strong><small>@{user.username} · {user.roles.join(", ")}</small></div><Status value={user.status} /></article>) : <Empty text="Chưa có tài khoản" />}</div></div>}
    <div className="panel"><PanelHeader title="Người bán" subtitle="Nhân sự nhận vé và giao tiền" count={sellers.length} /><div className="card-list">{sellers.length ? sellers.map((seller) => <article className="entity-card" key={seller.id}><div className="entity-avatar">{seller.code.slice(0, 2)}</div><div><strong>{seller.fullName}</strong><small>{seller.code} · {seller.phone || "Chưa có SĐT"}</small></div><Status value={seller.status} /></article>) : <Empty text="Chưa có seller" />}</div></div></section></div>;
}

function EntityModal({ kind, snapshot, onClose, onCreated }: { kind: ModalKind; snapshot: Snapshot; onClose: () => void; onCreated: (message: string) => Promise<void> }) {
  const [saving, setSaving] = useState(false); const [error, setError] = useState("");
  const [returnType, setReturnType] = useState("SELLER_TO_STORE");
  const [holderType, setHolderType] = useState("STORE");
  const [batchLines, setBatchLines] = useState<BatchLineDraft[]>([emptyBatchLine(1)]);
  const [batchMeta, setBatchMeta] = useState({ agencyId: "", receiptCode: "", businessDate: today(), receivedAt: nowLocal(), note: "" });
  const [entryMode, setEntryMode] = useState<"manual" | "file">("manual");
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [importWarnings, setImportWarnings] = useState<string[]>([]);
  const title: Record<ModalKind, string> = { agency: "Thêm đại lý", user: "Tạo tài khoản", seller: "Thêm seller", batch: "Nhận lô vé", allocation: "Giao vé cho seller", return: "Tạo phiếu trả", adjustment: "Báo điều chỉnh tồn", cash: "Thêm giao dịch tiền" };
  const confirmedLines = snapshot.batches.filter((batch) => batch.status === "CONFIRMED").flatMap((batch) => batch.lines.map((line) => ({ ...line, receiptCode: batch.receiptCode })));
  const issuedLines = snapshot.allocations.filter((allocation) => allocation.status === "ISSUED").flatMap((allocation) => allocation.lines.map((line) => ({ ...line, sellerId: allocation.sellerId, sellerName: allocation.sellerName })));

  const updateBatchLine = (key: number, field: Exclude<keyof BatchLineDraft, "key">, value: string) => {
    setBatchLines((lines) => lines.map((line) => line.key === key ? { ...line, [field]: value } : line));
  };

  const previewImport = async () => {
    if (!importFile) { setError("Vui lòng chọn file CSV hoặc XLSX"); return; }
    setImporting(true); setError(""); setImportWarnings([]);
    try {
      const preview = await api<BatchImportPreview>(`/api/v1/batches/import/preview?fileName=${encodeURIComponent(importFile.name)}`, {
        method: "POST",
        headers: { "Content-Type": "application/octet-stream" },
        body: await importFile.arrayBuffer(),
      });
      const agency = snapshot.agencies.find((item) => item.code.toLowerCase() === preview.agencyCode?.toLowerCase());
      setBatchMeta({
        agencyId: agency ? String(agency.id) : "",
        receiptCode: preview.receiptCode ?? "",
        businessDate: preview.businessDate ?? today(),
        receivedAt: toLocalDateTime(preview.receivedAt),
        note: preview.note ?? "",
      });
      setBatchLines(preview.lines.map((line, index) => ({
        ...line,
        key: Date.now() + index,
        quantityReceived: String(line.quantityReceived),
        unitCost: String(line.unitCost),
        unitSalePrice: String(line.unitSalePrice),
        returnCutoffAt: toLocalDateTime(line.returnCutoffAt),
        serialFrom: line.serialFrom ?? "",
        serialTo: line.serialTo ?? "",
      })));
      setImportWarnings(agency || !preview.agencyCode ? preview.warnings : [...preview.warnings, `Không tìm thấy đại lý mã ${preview.agencyCode}; hãy chọn thủ công.`]);
      setEntryMode("manual");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể đọc file"); }
    finally { setImporting(false); }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setSaving(true); setError(""); const form = new FormData(event.currentTarget);
    let path = ""; let payload: Record<string, unknown> = {}; let message = "Đã tạo dữ liệu mới";
    const value = (name: string) => String(form.get(name) ?? "").trim();
    try {
      if (kind === "agency") { path = "/api/v1/agencies"; payload = { code: value("code"), name: value("name"), contactName: value("contactName") || null, phone: value("phone") || null }; message = "Đã thêm đại lý"; }
      if (kind === "user") { path = "/api/v1/users"; payload = { username: value("username"), password: value("password"), fullName: value("fullName"), roles: form.getAll("roles") }; message = "Đã tạo tài khoản"; }
      if (kind === "seller") { path = "/api/v1/sellers"; payload = { userId: value("userId") ? Number(value("userId")) : null, code: value("code"), fullName: value("fullName"), phone: value("phone") || null }; message = "Đã thêm seller"; }
      if (kind === "batch") { path = "/api/v1/batches"; payload = { agencyId: Number(value("agencyId")), receiptCode: value("receiptCode"), businessDate: value("businessDate"), receivedAt: new Date(value("receivedAt")).toISOString(), note: value("note") || null, lines: batchLines.map((line) => ({ issuerName: line.issuerName, provinceCode: line.provinceCode, region: line.region, drawDate: line.drawDate, returnCutoffAt: new Date(line.returnCutoffAt).toISOString(), quantityReceived: Number(line.quantityReceived), unitCost: Number(line.unitCost), unitSalePrice: Number(line.unitSalePrice), serialFrom: line.serialFrom || null, serialTo: line.serialTo || null })) }; message = "Đã tạo lô vé bản nháp và ghi nhận kỳ vé"; }
      if (kind === "allocation") { path = "/api/v1/allocations"; payload = { sellerId: Number(value("sellerId")), businessDate: value("businessDate"), note: value("note") || null, lines: [{ batchLineId: Number(value("batchLineId")), quantity: Number(value("quantity")) }] }; message = "Đã tạo phiếu giao vé"; }
      if (kind === "return") { path = "/api/v1/returns"; const allocationLine = issuedLines.find((line) => line.id === Number(value("allocationLineId"))); payload = { returnType, sellerId: returnType === "SELLER_TO_STORE" ? Number(value("sellerId")) : null, agencyId: returnType === "STORE_TO_AGENCY" ? Number(value("agencyId")) : null, businessDate: value("businessDate"), note: value("note") || null, lines: [{ batchLineId: returnType === "SELLER_TO_STORE" ? allocationLine?.batchLineId : Number(value("batchLineId")), allocationLineId: returnType === "SELLER_TO_STORE" ? Number(value("allocationLineId")) : null, quantity: Number(value("quantity")) }] }; message = "Đã tạo phiếu trả vé"; }
      if (kind === "adjustment") { path = "/api/v1/inventory-adjustments"; const allocationLine = issuedLines.find((line) => line.id === Number(value("allocationLineId"))); payload = { batchLineId: holderType === "SELLER" ? allocationLine?.batchLineId : Number(value("batchLineId")), holderType, sellerId: holderType === "SELLER" ? Number(value("sellerId")) : null, allocationLineId: holderType === "SELLER" ? Number(value("allocationLineId")) : null, adjustmentType: value("adjustmentType"), direction: value("direction"), quantity: Number(value("quantity")), reason: value("reason") }; message = "Đã gửi điều chỉnh chờ duyệt"; }
      if (kind === "cash") { path = "/api/v1/cash-transactions"; payload = { sellerId: value("sellerId") ? Number(value("sellerId")) : null, businessDate: value("businessDate"), direction: value("direction"), transactionType: value("transactionType"), paymentMethod: value("paymentMethod"), amount: Number(value("amount")), occurredAt: new Date(value("occurredAt")).toISOString(), note: value("note") || null }; message = "Đã tạo giao dịch chờ ghi sổ"; }
      await api(path, { method: "POST", body: JSON.stringify(payload) }); await onCreated(message);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Không thể lưu dữ liệu"); }
    finally { setSaving(false); }
  };

  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}><section className="modal-card" role="dialog" aria-modal="true" aria-label={title[kind]}><header><div><p className="eyebrow">Nhập liệu</p><h2>{title[kind]}</h2></div><button className="close-button" onClick={onClose} aria-label="Đóng">×</button></header><form onSubmit={submit} className="modal-form">
    {kind === "agency" && <><Field label="Mã đại lý"><input name="code" placeholder="DL01" required /></Field><Field label="Tên đại lý"><input name="name" placeholder="Đại lý Minh Tâm" required /></Field><div className="form-grid"><Field label="Người liên hệ"><input name="contactName" /></Field><Field label="Số điện thoại"><input name="phone" inputMode="tel" /></Field></div></>}
    {kind === "user" && <><Field label="Họ và tên"><input name="fullName" required /></Field><div className="form-grid"><Field label="Tên đăng nhập"><input name="username" autoComplete="off" required /></Field><Field label="Mật khẩu"><input type="password" name="password" minLength={8} autoComplete="new-password" required /></Field></div><fieldset><legend>Vai trò</legend><div className="check-row"><label><input type="checkbox" name="roles" value="MANAGER" /> Quản lý</label><label><input type="checkbox" name="roles" value="SELLER" /> Seller</label><label><input type="checkbox" name="roles" value="OWNER" /> Owner</label></div></fieldset></>}
    {kind === "seller" && <><Field label="Họ và tên"><input name="fullName" required /></Field><div className="form-grid"><Field label="Mã seller"><input name="code" placeholder="NV01" required /></Field><Field label="Số điện thoại"><input name="phone" inputMode="tel" /></Field></div><Field label="Liên kết tài khoản (không bắt buộc)"><select name="userId"><option value="">Chưa liên kết</option>{snapshot.users.map((user) => <option key={user.id} value={user.id}>{user.fullName} (@{user.username})</option>)}</select></Field></>}
    {kind === "batch" && <>
      <div className="entry-tabs" role="tablist"><button type="button" className={entryMode === "manual" ? "active" : ""} onClick={() => setEntryMode("manual")}>Nhập thủ công</button><button type="button" className={entryMode === "file" ? "active" : ""} onClick={() => setEntryMode("file")}>Tải file CSV/XLSX</button></div>
      {entryMode === "file" ? <div className="import-panel"><div className="upload-box"><strong>Chọn bảng kê từ đại lý</strong><p>Hỗ trợ CSV/XLSX, tối đa 5 MB và 500 dòng. File chỉ được đọc để xem trước, chưa lưu vào hệ thống.</p><input type="file" accept=".csv,.xlsx" onChange={(event) => setImportFile(event.target.files?.[0] ?? null)} /></div><div className="import-actions"><a href="/templates/lo-ve-mau.csv" download>Tải file mẫu</a><button type="button" className="primary-button" disabled={!importFile || importing} onClick={() => void previewImport()}>{importing ? "Đang trích xuất…" : "Trích xuất & xem trước"}</button></div></div> : <>
        {importWarnings.length > 0 && <div className="import-warning"><strong>Đã trích xuất file — hãy kiểm tra lại trước khi lưu.</strong>{importWarnings.map((warning) => <p key={warning}>• {warning}</p>)}</div>}
        <div className="form-grid"><Field label="Đại lý"><select name="agencyId" value={batchMeta.agencyId} onChange={(event) => setBatchMeta((value) => ({ ...value, agencyId: event.target.value }))} required><option value="">Chọn đại lý</option>{snapshot.agencies.filter((agency) => agency.active).map((agency) => <option key={agency.id} value={agency.id}>{agency.name} ({agency.code})</option>)}</select></Field><Field label="Mã phiếu nhận"><input name="receiptCode" placeholder="PN-2026-001" value={batchMeta.receiptCode} onChange={(event) => setBatchMeta((value) => ({ ...value, receiptCode: event.target.value }))} required /></Field></div>
        <div className="form-grid"><Field label="Ngày bán"><input type="date" name="businessDate" value={batchMeta.businessDate} onChange={(event) => setBatchMeta((value) => ({ ...value, businessDate: event.target.value }))} required /></Field><Field label="Thời điểm nhận"><input type="datetime-local" name="receivedAt" value={batchMeta.receivedAt} onChange={(event) => setBatchMeta((value) => ({ ...value, receivedAt: event.target.value }))} required /></Field></div>
        <div className="line-editor"><div className="line-editor-head"><div><strong>Chi tiết vé</strong><small>Kỳ vé được tự ghi nhận từ thông tin bên dưới</small></div><button type="button" onClick={() => setBatchLines((lines) => [...lines, emptyBatchLine()])}>＋ Thêm dòng</button></div>{batchLines.map((line, index) => <div className="batch-entry-card" key={line.key}><div className="batch-entry-head"><strong>Dòng {index + 1}</strong>{batchLines.length > 1 && <button type="button" className="remove-line" onClick={() => setBatchLines((lines) => lines.filter((item) => item.key !== line.key))} aria-label={`Xóa dòng ${index + 1}`}>×</button>}</div><div className="form-grid three"><Field label="Đơn vị phát hành"><input value={line.issuerName} onChange={(event) => updateBatchLine(line.key, "issuerName", event.target.value)} placeholder="XSKT TP.HCM" required /></Field><Field label="Mã tỉnh/đài"><input value={line.provinceCode} onChange={(event) => updateBatchLine(line.key, "provinceCode", event.target.value)} placeholder="HCM" required /></Field><Field label="Khu vực"><select value={line.region} onChange={(event) => updateBatchLine(line.key, "region", event.target.value)}><option value="SOUTH">Miền Nam</option><option value="CENTRAL">Miền Trung</option><option value="NORTH">Miền Bắc</option></select></Field></div><div className="form-grid three"><Field label="Ngày quay"><input type="date" value={line.drawDate} onChange={(event) => updateBatchLine(line.key, "drawDate", event.target.value)} required /></Field><Field label="Hạn trả vé"><input type="datetime-local" value={line.returnCutoffAt} onChange={(event) => updateBatchLine(line.key, "returnCutoffAt", event.target.value)} required /></Field><Field label="Số lượng"><input type="number" min="1" value={line.quantityReceived} onChange={(event) => updateBatchLine(line.key, "quantityReceived", event.target.value)} required /></Field></div><div className="form-grid four"><Field label="Giá nhập"><input type="number" min="0" value={line.unitCost} onChange={(event) => updateBatchLine(line.key, "unitCost", event.target.value)} required /></Field><Field label="Giá bán"><input type="number" min="1" value={line.unitSalePrice} onChange={(event) => updateBatchLine(line.key, "unitSalePrice", event.target.value)} required /></Field><Field label="Serial từ (tùy chọn)"><input value={line.serialFrom} onChange={(event) => updateBatchLine(line.key, "serialFrom", event.target.value)} /></Field><Field label="Serial đến (tùy chọn)"><input value={line.serialTo} onChange={(event) => updateBatchLine(line.key, "serialTo", event.target.value)} /></Field></div></div>)}</div>
        <Field label="Ghi chú"><textarea name="note" rows={2} value={batchMeta.note} onChange={(event) => setBatchMeta((value) => ({ ...value, note: event.target.value }))} /></Field>
      </>}
    </>}
    {kind === "allocation" && <><div className="form-grid"><Field label="Seller"><select name="sellerId" required><option value="">Chọn seller</option>{snapshot.sellers.filter((s) => s.status === "ACTIVE").map((seller) => <option key={seller.id} value={seller.id}>{seller.fullName}</option>)}</select></Field><Field label="Ngày bán"><input type="date" name="businessDate" defaultValue={today()} required /></Field></div><Field label="Dòng vé"><select name="batchLineId" required><option value="">Chọn lô / kỳ quay</option>{confirmedLines.map((line) => <option key={line.id} value={line.id}>{line.receiptCode} · {line.provinceCode} · {formatDate(line.drawDate)}</option>)}</select></Field><Field label="Số lượng giao"><input type="number" name="quantity" min="1" defaultValue="100" required /></Field><Field label="Ghi chú"><textarea name="note" rows={2} /></Field></>}
    {kind === "return" && <><Field label="Loại trả"><select value={returnType} onChange={(e) => setReturnType(e.target.value)}><option value="SELLER_TO_STORE">Seller trả cửa hàng</option><option value="STORE_TO_AGENCY">Cửa hàng trả đại lý</option></select></Field>{returnType === "SELLER_TO_STORE" ? <><Field label="Seller"><select name="sellerId" required><option value="">Chọn seller</option>{snapshot.sellers.map((seller) => <option key={seller.id} value={seller.id}>{seller.fullName}</option>)}</select></Field><Field label="Dòng vé đã giao"><select name="allocationLineId" required><option value="">Chọn dòng vé</option>{issuedLines.map((line) => <option key={line.id} value={line.id}>{line.sellerName} · {line.provinceCode} · {formatDate(line.drawDate)}</option>)}</select></Field></> : <><Field label="Đại lý"><select name="agencyId" required><option value="">Chọn đại lý</option>{snapshot.agencies.map((agency) => <option key={agency.id} value={agency.id}>{agency.name}</option>)}</select></Field><Field label="Dòng vé tại cửa hàng"><select name="batchLineId" required><option value="">Chọn dòng vé</option>{confirmedLines.map((line) => <option key={line.id} value={line.id}>{line.receiptCode} · {line.provinceCode}</option>)}</select></Field></>}<div className="form-grid"><Field label="Ngày bán"><input type="date" name="businessDate" defaultValue={today()} required /></Field><Field label="Số lượng"><input type="number" name="quantity" min="1" defaultValue="1" required /></Field></div><Field label="Ghi chú"><textarea name="note" rows={2} /></Field></>}
    {kind === "adjustment" && <><div className="form-grid"><Field label="Vị trí tồn"><select value={holderType} onChange={(e) => setHolderType(e.target.value)}><option value="STORE">Tại cửa hàng</option><option value="SELLER">Tại seller</option></select></Field><Field label="Loại điều chỉnh"><select name="adjustmentType" defaultValue="LOST"><option value="LOST">Thất thoát</option><option value="DAMAGED">Hư hỏng</option><option value="FOUND">Tìm thấy</option><option value="CORRECTION">Sửa sai</option></select></Field></div>{holderType === "SELLER" ? <><Field label="Seller"><select name="sellerId" required><option value="">Chọn seller</option>{snapshot.sellers.map((seller) => <option key={seller.id} value={seller.id}>{seller.fullName}</option>)}</select></Field><Field label="Dòng vé đã giao"><select name="allocationLineId" required><option value="">Chọn dòng vé</option>{issuedLines.map((line) => <option key={line.id} value={line.id}>{line.sellerName} · {line.provinceCode}</option>)}</select></Field></> : <Field label="Dòng vé tại cửa hàng"><select name="batchLineId" required><option value="">Chọn dòng vé</option>{confirmedLines.map((line) => <option key={line.id} value={line.id}>{line.receiptCode} · {line.provinceCode}</option>)}</select></Field>}<div className="form-grid"><Field label="Chiều điều chỉnh"><select name="direction" defaultValue="DECREASE"><option value="DECREASE">Giảm tồn</option><option value="INCREASE">Tăng tồn</option></select></Field><Field label="Số lượng"><input type="number" name="quantity" min="1" defaultValue="1" required /></Field></div><Field label="Lý do"><textarea name="reason" rows={3} required /></Field></>}
    {kind === "cash" && <><div className="form-grid"><Field label="Seller (để trống nếu tại quầy)"><select name="sellerId"><option value="">Giao dịch tại quầy</option>{snapshot.sellers.map((seller) => <option key={seller.id} value={seller.id}>{seller.fullName}</option>)}</select></Field><Field label="Ngày bán"><input type="date" name="businessDate" defaultValue={today()} required /></Field></div><div className="form-grid"><Field label="Chiều tiền"><select name="direction" defaultValue="IN"><option value="IN">Thu vào</option><option value="OUT">Chi ra</option></select></Field><Field label="Loại giao dịch"><select name="transactionType" defaultValue="SALES_COLLECTION"><option value="SALES_COLLECTION">Thu tiền bán vé</option><option value="REFUND">Hoàn tiền</option><option value="ADJUSTMENT">Điều chỉnh</option><option value="EXPENSE">Chi phí</option><option value="AGENCY_PAYMENT">Thanh toán đại lý</option></select></Field></div><div className="form-grid"><Field label="Số tiền"><input type="number" name="amount" min="1" step="1000" required /></Field><Field label="Phương thức"><select name="paymentMethod" defaultValue="CASH"><option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản</option><option value="EWALLET">Ví điện tử</option></select></Field></div><Field label="Thời điểm"><input type="datetime-local" name="occurredAt" defaultValue={nowLocal()} required /></Field><Field label="Ghi chú"><textarea name="note" rows={2} /></Field></>}
    {error && <p className="form-error">{error}</p>}<footer><button type="button" className="secondary-button" onClick={onClose}>Hủy</button><button className="primary-button" disabled={saving || (kind === "batch" && entryMode === "file")}>{saving ? "Đang lưu…" : "Lưu dữ liệu"}</button></footer>
  </form></section></div>;
}

function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="field">{label}{children}</label>; }
function PanelHeader({ title, subtitle, count }: { title: string; subtitle: string; count?: number }) { return <header className="panel-header"><div><h2>{title}</h2><p>{subtitle}</p></div>{count !== undefined && <span className="count-badge">{number.format(count)}</span>}</header>; }
function Metric({ label, value, hint, tone }: { label: string; value: string; hint: string; tone: string }) { return <article className={`metric-card ${tone}`}><div className="metric-top"><span>{label}</span><i /></div><strong>{value}</strong><small>{hint}</small></article>; }
function Status({ value }: { value: string }) { const tone = ["CONFIRMED", "POSTED", "APPROVED", "CLOSED", "ACTIVE", "ISSUED"].includes(value) ? "success" : ["PENDING", "DRAFT", "REVIEW_REQUIRED", "OPEN"].includes(value) ? "warning" : ["REJECTED", "CANCELLED", "VOIDED", "DISABLED"].includes(value) ? "danger" : "neutral"; return <span className={`status ${tone}`}>{STATUS_LABELS[value] ?? value}</span>; }
function Empty({ text }: { text: string }) { return <div className="empty-state"><span>∅</span><p>{text}</p></div>; }
function DataTable({ headers, empty, children }: { headers: string[]; empty: string; children: ReactNode }) { const hasChildren = Array.isArray(children) ? children.length > 0 : Boolean(children); return hasChildren ? <div className="table-wrap"><table><thead><tr>{headers.map((header, index) => <th key={`${header}-${index}`}>{header}</th>)}</tr></thead><tbody>{children}</tbody></table></div> : <Empty text={empty} />; }
