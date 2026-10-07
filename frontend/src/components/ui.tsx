import type { ReactNode } from "react";
import { AlertCircle, CircleDollarSign } from "lucide-react";
import { readableStatus } from "@/lib/api";

export function PageHeading({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description: string; action?: ReactNode }) {
  return <div className="page-heading"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1><p>{description}</p></div>{action && <div className="heading-action">{action}</div>}</div>;
}
export function StatusBadge({ status }: { status: string }) { return <span className={`status-pill status-${status.toLowerCase()}`}>{readableStatus(status)}</span>; }
export function Loading({ label = "Cargando información…" }: { label?: string }) { return <div className="inline-loading"><span className="spinner" />{label}</div>; }
export function ErrorBox({ message }: { message: string }) { return <div className="inline-error"><AlertCircle size={15} /> {message}</div>; }
export function Empty({ title, detail }: { title: string; detail: string }) { return <div className="empty-state"><span className="empty-icon"><CircleDollarSign size={19} /></span><strong>{title}</strong><p>{detail}</p></div>; }
