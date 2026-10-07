export class ApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); this.name = "ApiError"; }
}

export async function apiRequest<T>(token: string, path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  let response: Response;
  try { response = await fetch(path, { ...init, headers }); }
  catch { throw new ApiError("No se pudo conectar con FinPay. Comprueba que el backend esté iniciado.", 0); }
  if (!response.ok) {
    let message = "La solicitud no se pudo completar.";
    try {
      const body = await response.json() as { message?: string; detail?: string; title?: string };
      message = body.message ?? body.detail ?? body.title ?? message;
    } catch { /* Some error responses have no JSON body. */ }
    if (response.status === 401) message = "Tu sesión ha caducado. Vuelve a iniciar sesión.";
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat("es-ES", { style: "currency", currency }).format(value);
}
export function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-ES", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
export function readableStatus(value: string) {
  return ({ ACTIVE: "Activa", BLOCKED: "Bloqueada", CLOSED: "Cerrada", CREATED: "Creado", PENDING: "Pendiente", AUTHORIZED: "Autorizado", CAPTURED: "Capturado", COMPLETED: "Completado", REJECTED: "Rechazado", FAILED: "Fallido", COMPLETED_TRANSFER: "Completada" } as Record<string, string>)[value] ?? value;
}
