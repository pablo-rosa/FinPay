"use client";
import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, Plus, Wallet } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { apiRequest, formatDate, formatMoney } from "@/lib/api";
import type { Account } from "@/lib/types";
import { Empty, ErrorBox, Loading, PageHeading, StatusBadge } from "@/components/ui";

export default function AccountsPage() {
  const { token } = useAuth(); const qc = useQueryClient(); const [currency, setCurrency] = useState("EUR"); const [error, setError] = useState(""); const [copied, setCopied] = useState("");
  const query = useQuery({ queryKey: ["accounts", token], queryFn: () => apiRequest<Account[]>(token!, "/api/accounts") });
  const create = useMutation({ mutationFn: () => apiRequest<Account>(token!, "/api/accounts", { method: "POST", body: JSON.stringify({ currency }) }), onSuccess: () => { setError(""); void qc.invalidateQueries({ queryKey: ["accounts"] }); }, onError: e => setError(e instanceof Error ? e.message : "No se pudo crear la cuenta.") });
  async function submit(e: FormEvent) { e.preventDefault(); create.mutate(); }
  async function copy(id: string) { await navigator.clipboard.writeText(id); setCopied(id); window.setTimeout(() => setCopied(""), 1600); }
  return <><PageHeading eyebrow="Productos" title="Cuentas" description="Consulta tus cuentas o crea una cuenta de demostración." />
    <div className="section-grid"><section>{query.isPending ? <Loading /> : query.data?.length ? <div className="account-grid">{query.data.map(a => <article className="account-card" key={a.id}><div className="account-card-top"><span className="account-symbol"><Wallet size={19} /></span><StatusBadge status={a.status} /></div><h3>Cuenta FinPay</h3><span className="account-number">{a.accountNumber}</span><p className="big-balance">{formatMoney(a.balance, a.currency)}</p><div className="account-card-bottom"><span>Creada {formatDate(a.createdAt)}</span><button className="copy-button" onClick={() => void copy(a.id)}><span>{copied === a.id ? <Check size={12} /> : <Copy size={12} />}</span>{copied === a.id ? "Copiado" : "Copiar ID"}</button></div></article>)}</div> : <div className="panel"><Empty title="Todavía no hay cuentas" detail="Crea una cuenta para poder empezar a explorar FinPay." /></div>}</section>
    <aside className="panel form-panel"><h2>Abrir una cuenta</h2><p>Las cuentas nuevas comienzan con saldo cero. Puedes crear distintas monedas.</p><form onSubmit={submit}><div className="field"><label htmlFor="currency">Moneda</label><select id="currency" value={currency} onChange={e => setCurrency(e.target.value)}><option value="EUR">EUR · Euro</option><option value="USD">USD · Dólar estadounidense</option><option value="GBP">GBP · Libra esterlina</option></select></div>{error && <ErrorBox message={error} />}<button className="primary-button wide" disabled={create.isPending}><Plus size={15} />{create.isPending ? "Creando…" : "Crear cuenta"}</button></form><div className="notice" style={{ marginTop: 18 }}><Wallet size={15} /> El backend no incluye aún una operación de ingreso de fondos; el saldo inicial es cero.</div></aside></div></>;
}
