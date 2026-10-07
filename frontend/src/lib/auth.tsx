"use client";

import { createContext, useCallback, useContext, useMemo, useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "./api";

type AuthContextValue = { token: string | null; ready: boolean; signIn: (email: string, password: string) => Promise<void>; signOut: () => Promise<void> };
const AuthContext = createContext<AuthContextValue | null>(null);
const listeners = new Set<() => void>();
function subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; }
function getTokenSnapshot() { return sessionStorage.getItem("finpay_token") ?? "none"; }
function getServerSnapshot() { return ""; }
function publishToken() { listeners.forEach(listener => listener()); }

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const snapshot = useSyncExternalStore(subscribe, getTokenSnapshot, getServerSnapshot);
  const ready = snapshot !== "";
  const token = snapshot === "" || snapshot === "none" ? null : snapshot;
  const queryClient = useQueryClient();
  const signIn = useCallback(async (email: string, password: string) => {
    const result = await fetch("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
    if (!result.ok) {
      let message = "Email o contraseña incorrectos.";
      try { const body = await result.json() as { message?: string }; message = body.message ?? message; } catch { /* Keep a useful default. */ }
      throw new Error(message);
    }
    const body = await result.json() as { accessToken: string };
    sessionStorage.setItem("finpay_token", body.accessToken);
    publishToken();
  }, []);
  const signOut = useCallback(async () => {
    const current = token;
    sessionStorage.removeItem("finpay_token"); publishToken(); queryClient.clear();
    if (current) { try { await apiRequest<void>(current, "/api/auth/logout", { method: "POST" }); } catch { /* Local sign-out remains effective if the API is unavailable. */ } }
  }, [queryClient, token]);
  const value = useMemo(() => ({ token, ready, signIn, signOut }), [token, ready, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error("useAuth debe usarse dentro de AuthProvider"); return value; }
