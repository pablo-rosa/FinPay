"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Wallet } from "lucide-react";
import { useAuth } from "@/lib/auth";

export default function LoginPage() {
  const { ready, token, signIn, signInDemo } = useAuth(); const router = useRouter();
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  useEffect(() => { if (ready && token) router.replace("/dashboard"); }, [ready, token, router]);
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setBusy(true); setError(""); try { await signIn(email, password); router.replace("/dashboard"); } catch (cause) { setError(cause instanceof Error ? cause.message : "No se pudo iniciar sesión."); } finally { setBusy(false); } }
  async function startDemo() { setBusy(true); setError(""); try { await signInDemo(); router.replace("/dashboard"); } catch (cause) { setError(cause instanceof Error ? cause.message : "No se pudo iniciar la demo."); } finally { setBusy(false); } }
  return <main className="login-page"><section className="login-art"><a className="brand" href="/login"><span className="brand-mark"><Wallet size={18} /></span>finpay</a><div className="login-copy"><p className="eyebrow">Tus finanzas, en orden</p><h1>Una forma más clara de gestionar tu dinero.</h1><p>Consulta tus cuentas, explora transferencias y sigue cada pago desde un único lugar.</p></div><span className="art-note">Una experiencia de demostración. Sin dinero real.</span></section><section className="login-panel"><div className="login-card"><p className="eyebrow">Bienvenido de nuevo</p><h2>Inicia sesión</h2><p className="login-subtitle">Accede a tu espacio personal de FinPay.</p><form onSubmit={submit}><div className="field"><label htmlFor="email">Correo electrónico</label><input id="email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="tu@email.com" /></div><div className="field"><label htmlFor="password">Contraseña</label><input id="password" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="Tu contraseña" /></div>{error && <p className="form-error" role="alert">{error}</p>}<button className="primary-button wide" type="submit" disabled={busy}>{busy ? "Conectando…" : <>Entrar <ArrowRight size={16} /></>}</button></form><div className="login-divider"><span>o explora sin registrarte</span></div><button className="secondary-button wide demo-login-button" type="button" onClick={() => void startDemo()} disabled={busy}>{busy ? "Preparando demo…" : <>Probar FinPay Demo <ArrowRight size={16} /></>}</button><p className="form-hint">Sesión aislada con 10.000 EUR ficticios. No se procesa dinero real.</p></div></section></main>;
}
