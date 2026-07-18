import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Tâm Lottery — Quản lý cửa hàng",
  description: "Quản lý vé số, giao nhận, tiền và đối soát hằng ngày.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="vi"><body>{children}</body></html>;
}
