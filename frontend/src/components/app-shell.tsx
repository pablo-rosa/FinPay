"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Banknote, LayoutDashboard, LogOut, ReceiptText, Wallet } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { apiRequest } from "@/lib/api";
import type { User } from "@/lib/types";

const links = [
  { href: "/dashboard", label: "Resumen", icon: LayoutDashboard },
  { href: "/accounts", label: "Cuentas", icon: Wallet },
  { href: "/transfers", label: "Transferencias", icon: ArrowLeftRight },
  { href: "/payments", label: "Pagos", icon: Banknote },
  { href: "/transactions", label: "Actividad", icon: ReceiptText },
];
function Navigation({ mobile = false }: { mobile?: boolean }) {
  const path = usePathname();
  return <nav className={mobile ? "mobile-nav" : "nav-list"}>{links.map(({ href, label, icon: Icon }) => <Link key={href} href={href} className={`nav-link ${path === href ? "active" : ""}`}><Icon size={17} /><span>{label}</span></Link>)}</nav>;
}
export function AppShell({ children }: { children: React.ReactNode }) {
  const { token, ready, signOut } = useAuth(); const router = useRouter();
  const profile = useQuery({ queryKey: ["me", token], queryFn: () => apiRequest<User>(token!, "/api/users/me"), enabled: ready && !!token });
  useEffect(() => { if (ready && !token) router.replace("/login"); }, [ready, token, router]);
  useEffect(() => { if (profile.error && (profile.error as { status?: number }).status === 401) void signOut(); }, [profile.error, signOut]);
  useEffect(() => { if (ready && token && profile.isError && (profile.error as { status?: number }).status === 401) router.replace("/login"); }, [ready, token, profile.isError, profile.error, router]);
  if (!ready || !token || profile.isPending) return <div className="route-loading"><span className="spinner" />Abriendo tu espacio FinPay…</div>;
  const email = profile.data?.email ?? "Tu cuenta";
  return <div className="app-shell"><aside className="sidebar"><Link href="/dashboard" className="brand"><span className="brand-mark"><Wallet size={18} /></span>finpay</Link><p className="workspace-label">Tu espacio</p><Navigation /><div className="sidebar-spacer" /><div className="sidebar-note"><strong>Modo demostración</strong><span>FinPay es un proyecto de portfolio. No procesa dinero real.</span></div><div className="sidebar-foot"><span className="avatar">{email.slice(0, 1).toUpperCase()}</span><div className="sidebar-user"><strong>{email}</strong><span>Cuenta personal</span></div><button className="logout-icon" title="Cerrar sesión" aria-label="Cerrar sesión" onClick={() => void signOut().then(() => router.replace("/login"))}><LogOut size={16} /></button></div></aside><main className="main-area"><header className="topbar"><span className="breadcrumb">FinPay <span aria-hidden="true">/</span> Espacio personal</span><div className="topbar-right"><span className="topbar-date">{new Intl.DateTimeFormat("es-ES", { dateStyle: "full" }).format(new Date())}</span><span className="avatar">{email.slice(0, 1).toUpperCase()}</span></div></header><div className="content">{children}</div></main><Navigation mobile /></div>;
}
