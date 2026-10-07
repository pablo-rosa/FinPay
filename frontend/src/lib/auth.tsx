"use client";

import { createContext, useCallback, useContext, useMemo, useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "./api";

type AuthContextValue = { token: string | null; ready: boolean; demo: boolean; signIn: (email: string, password: string) => Promise<void>; signInDemo: () => Promise<void>; signOut: () => Promise<void> };
const AuthContext = createContext<AuthContextValue | null>(null);
const listeners = new Set<() => void>();
function subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; }
function getTokenSnapshot() { return sessionStorage.getItem("finpay_token") ?? "none"; }
function getServerSnapshot() { return ""; }
function getDemoSnapshot() { return sessionStorage.getItem("finpay_demo") ?? "false"; }
function getServerDemoSnapshot() { return "false"; }
function publishToken() { listeners.forEach(listener => listener()); }

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const snapshot = useSyncExternalStore(subscribe, getTokenSnapshot, getServerSnapshot);
  const demoSnapshot = useSyncExternalStore(subscribe, getDemoSnapshot, getServerDemoSnapshot);
  const ready = snapshot !== "";
  const token = snapshot === "" || snapshot === "none" ? null : snapshot;
  const demo = ready && demoSnapshot === "true";
  const queryClient = useQueryClient();
  const signIn = useCallback(async (email: string, password: string) => {
    const result = await fetch("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
    if (!result.ok) {
      let message = "Email o contraseña incorrectos.";
      try { const body = await result.json() as { message?: string }; message = body.message ?? message; } catch { /* Keep a useful default. */ }
      throw new Error(message);
    }
    const body = await result.json() as { accessToken: string };
    sessionStorage.removeItem("finpay_demo");
    sessionStorage.setItem("finpay_token", body.accessToken);
    publishToken();
  }, []);
  const signInDemo = useCallback(async () => {
    let result: Response;
    try { result = await fetch("/api/demo/session", { method: "POST" }); }
    catch { throw new Error("No se pudo conectar con FinPay. Comprueba que el backend esté iniciado."); }
    if (!result.ok) {
      let message = "No se pudo iniciar la demo.";
      try { const body = await result.json() as { message?: string }; message = body.message ?? message; } catch { /* Keep a useful default. */ }
      throw new Error(message);
    }
    const body = await result.json() as { accessToken: string; demo: boolean };
    sessionStorage.setItem("finpay_demo", body.demo ? "true" : "false");
    sessionStorage.setItem("finpay_token", body.accessToken);
    publishToken();
  }, []);
  const signOut = useCallback(async () => {
    const current = token;
    sessionStorage.removeItem("finpay_token"); sessionStorage.removeItem("finpay_demo"); publishToken(); queryClient.clear();
    if (current) { try { await apiRequest<void>(current, "/api/auth/logout", { method: "POST" }); } catch { /* Local sign-out remains effective if the API is unavailable. */ } }
  }, [queryClient, token]);
  const value = useMemo(() => ({ token, ready, demo, signIn, signInDemo, signOut }), [token, ready, demo, signIn, signInDemo, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error("useAuth debe usarse dentro de AuthProvider"); return value; }
