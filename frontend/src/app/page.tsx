"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";

export default function HomePage() {
  const router = useRouter();
  const { ready, token } = useAuth();

  useEffect(() => {
    if (ready) router.replace(token ? "/dashboard" : "/login");
  }, [ready, router, token]);

  return <div className="route-loading"><span className="spinner" />Preparando tu espacio FinPay…</div>;
}
