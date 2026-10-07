"use client";
import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Banknote, Check, CircleDollarSign, Send, X } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { apiRequest, formatDate, formatMoney } from "@/lib/api";
import type { Account, Payment } from "@/lib/types";
import { Empty, ErrorBox, Loading, PageHeading, StatusBadge } from "@/components/ui";

export default function PaymentsPage() {
  const { token } = useAuth(); const qc = useQueryClient(); const [source, setSource] = useState(""); const [destination, setDestination] = useState(""); const [amount, setAmount] = useState(""); const [error, setError] = useState("");
  const accounts = useQuery({ queryKey: ["accounts", token], queryFn: () => apiRequest<Account[]>(token!, "/api/accounts") });
  const payments = useQuery({ queryKey: ["payments", token], queryFn: () => apiRequest<Payment[]>(token!, "/api/payments") });
  const active = (accounts.data ?? []).filter(a => a.status === "ACTIVE");
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["payments"] }); void qc.invalidateQueries({ queryKey: ["accounts"] }); void qc.invalidateQueries({ queryKey: ["transfers"] }); };
  const create = useMutation({ mutationFn: () => apiRequest<Payment>(token!, "/api/payments", { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify({ sourceAccountId: source, destinationAccountId: destination.trim(), amount: Number(amount) }) }), onSuccess: () => { setAmount(""); setError(""); refresh(); }, onError: e => setError(e instanceof Error ? e.message : "No se pudo crear el pago.") });
  const action = useMutation({ mutationFn: ({ id, step }: { id: string; step: string }) => apiRequest<Payment>(token!, `/api/payments/${id}/${step}`, { method: "POST" }), onSuccess: () => { setError(""); refresh(); }, onError: e => setError(e instanceof Error ? e.message : "No se pudo actualizar el pago.") });
  function submit(e: FormEvent) { e.preventDefault(); setError(""); create.mutate(); }
  function buttons(p: Payment) {
    const act = (step: string, label: string, icon: React.ReactNode, style = "secondary-button") => <button key={step} className={style} disabled={action.isPending} onClick={() => action.mutate({ id: p.id, step })}>{icon}{label}</button>;
    if (p.status === "CREATED") return <>{act("submit", "Enviar", <Send size={12} />, "primary-button")}{act("reject", "Rechazar", <X size={12} />, "danger-button")}</>;
    if (p.status === "PENDING") return <>{act("authorize", "Autorizar", <Check size={12} />, "primary-button")}{act("reject", "Rechazar", <X size={12} />, "danger-button")}</>;
    if (p.status === "AUTHORIZED") return act("capture", "Capturar fondos", <CircleDollarSign size={12} />, "primary-button");
    if (p.status === "CAPTURED") return act("complete", "Completar", <Check size={12} />, "primary-button");
    return null;
  }
  return <><PageHeading eyebrow="Operaciones" title="Pagos" description="Crea y avanza pagos por cada etapa de su ciclo de vida." />
    <div className="section-grid"><section><div className="panel-header" style={{ paddingInline: 0, border: 0 }}><div><h2 className="panel-title">Tus pagos</h2><p className="panel-subtitle">Los cambios de estado se realizan manualmente en esta demo.</p></div></div>{payments.isPending ? <Loading /> : payments.data?.length ? <div className="payments-list">{payments.data.map(p => <article className="payment-card" key={p.id}><div className="payment-card-top"><div><h3>Pago · {p.id.slice(0, 8)}…</h3><p>{formatDate(p.createdAt)} · Destino {p.destinationAccountId.slice(0, 8)}…</p></div><StatusBadge status={p.status} /></div><div className="payment-card-bottom"><strong>{formatMoney(p.amount, p.currency)}</strong><div className="payment-actions">{buttons(p)}</div></div></article>)}</div> : <div className="panel"><Empty title="Aún no hay pagos" detail="Crea un pago de demostración para iniciar el flujo." /></div>}</section>
    <aside className="panel form-panel"><h2>Crear un pago</h2><p>Se asigna automáticamente una clave de idempotencia para evitar duplicados por reintento.</p><form onSubmit={submit}><div className="field"><label htmlFor="pay-source">Cuenta de origen</label><select id="pay-source" required value={source} onChange={e => setSource(e.target.value)}><option value="">Selecciona una cuenta</option>{active.map(a => <option key={a.id} value={a.id}>{a.accountNumber} · {a.currency} · {formatMoney(a.balance, a.currency)}</option>)}</select></div><div className="field"><label htmlFor="pay-destination">ID de cuenta de destino</label><input id="pay-destination" required value={destination} onChange={e => setDestination(e.target.value)} placeholder="UUID de la cuenta" /></div><div className="field"><label htmlFor="pay-amount">Importe</label><input id="pay-amount" required type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} placeholder="0,00" /></div>{error && <ErrorBox message={error} />}<button className="primary-button wide" disabled={!active.length || !source || create.isPending}><Banknote size={15} />{create.isPending ? "Creando…" : "Crear pago"}</button></form><div className="notice" style={{ marginTop: 18 }}>La autorización comprueba fondos, pero no los reserva. La captura vuelve a comprobar el saldo y registra el movimiento.</div></aside></div></>;
}
