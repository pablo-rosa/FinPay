"use client";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Banknote } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { apiRequest, formatDate, formatMoney } from "@/lib/api";
import type { Payment, Transfer } from "@/lib/types";
import { Empty, Loading, PageHeading, StatusBadge } from "@/components/ui";

export default function TransactionsPage() {
  const { token } = useAuth();
  const transfers = useQuery({ queryKey: ["transfers", token], queryFn: () => apiRequest<Transfer[]>(token!, "/api/transfers") });
  const payments = useQuery({ queryKey: ["payments", token], queryFn: () => apiRequest<Payment[]>(token!, "/api/payments") });
  const rows = [...(transfers.data ?? []).map(t => ({ id: t.id, kind: "Transferencia", source: t.sourceAccountId, destination: t.destinationAccountId, amount: t.amount, currency: t.currency, status: t.status, date: t.createdAt })), ...(payments.data ?? []).map(p => ({ id: p.id, kind: "Pago", source: p.sourceAccountId, destination: p.destinationAccountId, amount: p.amount, currency: p.currency, status: p.status, date: p.createdAt }))].sort((a, b) => b.date.localeCompare(a.date));
  return <><PageHeading eyebrow="Movimientos" title="Actividad" description="Vista conjunta del historial de transferencias y pagos." />{transfers.isPending || payments.isPending ? <Loading /> : rows.length ? <section className="panel"><div className="table-wrap"><table className="data-table"><thead><tr><th>Operación</th><th>Fecha</th><th>Origen</th><th>Destino</th><th>Importe</th><th>Estado</th></tr></thead><tbody>{rows.map(r => <tr key={`${r.kind}-${r.id}`}><td><strong style={{ display: "inline-flex", alignItems: "center", gap: 7 }}>{r.kind === "Pago" ? <Banknote size={14} /> : <ArrowLeftRight size={14} />}{r.kind}</strong></td><td>{formatDate(r.date)}</td><td title={r.source}>{r.source.slice(0, 8)}…</td><td title={r.destination}>{r.destination.slice(0, 8)}…</td><td><strong>{formatMoney(r.amount, r.currency)}</strong></td><td><StatusBadge status={r.status} /></td></tr>)}</tbody></table></div></section> : <div className="panel"><Empty title="Sin actividad todavía" detail="Tus pagos y transferencias aparecerán aquí cuando los crees." /></div>}<p className="form-hint" style={{ textAlign: "left" }}>Esta vista combina los endpoints de pagos y transferencias disponibles en el backend; aún no existe un endpoint general de transacciones.</p></>;
}
