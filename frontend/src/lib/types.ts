export type User = { id: string; email: string; roles: string[] };
export type Account = { id: string; accountNumber: string; currency: string; balance: number; status: "ACTIVE" | "BLOCKED" | "CLOSED"; createdAt: string; updatedAt: string; version: number };
export type Transfer = { id: string; sourceAccountId: string; destinationAccountId: string; amount: number; currency: string; status: string; createdAt: string };
export type Payment = { id: string; sourceAccountId: string; destinationAccountId: string; amount: number; currency: string; status: "CREATED" | "PENDING" | "AUTHORIZED" | "CAPTURED" | "COMPLETED" | "REJECTED" | "FAILED"; createdAt: string; updatedAt: string };
