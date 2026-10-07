
## Interfaz web (Fase 7)

La interfaz requiere Node.js 20.9 o posterior y npm. Con PostgreSQL iniciado, ejecuta el backend en una terminal (`mvn spring-boot:run`) y, en otra:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

Abre `http://localhost:3000` e inicia sesión con un usuario previamente registrado mediante el API. Next.js reenvía `/api/*` al backend; configura `FINPAY_API_URL` en `frontend/.env.local` si el backend no está en `http://localhost:8080`. La sesión del prototipo se guarda en `sessionStorage` y se elimina al cerrar sesión o cerrar la pestaña.

La interfaz incluye resumen, cuentas, transferencias, pagos y actividad. Las cuentas nuevas empiezan con saldo cero y el backend aún no tiene una operación para ingresar fondos. Para transferencias y pagos, el destino se introduce con el UUID de una cuenta. La actividad combina los historiales de pagos y transferencias porque aún no existe un endpoint general de transacciones. Las acciones de pago permiten avanzar manualmente por sus estados.

## Pruebas E2E (Fase 8)

Las pruebas requieren el backend accesible en `http://localhost:8080` con `FINPAY_DEMO_ENABLED=true`, PostgreSQL iniciado y el frontend en `http://localhost:3000`. Instala Chromium una vez y ejecuta:

```powershell
npx playwright install chromium
npm run test:e2e
```

Puedes cambiar la URL del frontend con `PLAYWRIGHT_BASE_URL`. Para apuntar el proxy a otro backend, configura `FINPAY_API_URL` en `.env.local`.

La Fase 7 del roadmap queda implementada. Las fases posteriores pueden ampliar el producto con funciones administrativas, fraude, notificaciones, auditoría y operaciones adicionales.
