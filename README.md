# FinPay

FinPay es una simulación de una plataforma bancaria y de pagos para practicar ingeniería de software. No mueve dinero real y no está preparada para operar como entidad financiera.

## Estado actual

El repositorio contiene un monolito modular desplegado como una aplicación Spring Boot, PostgreSQL, migraciones Flyway y una interfaz web Next.js. Las fases 0–7 del Prompt Maestro están implementadas. La Fase 8 ya tiene pruebas unitarias, de integración con Testcontainers y E2E; su verificación completa está pendiente porque la última ejecución no pudo acceder al daemon de Docker y no ejecutó las pruebas E2E. Las fases 9–18 siguen pendientes.

FinPay incluye registro y login, JWT con revocación, cuentas, ledger de doble partida, transferencias, ciclo de pagos con idempotencia en la creación, interfaz web y una demo de portfolio con fondos ficticios aislados por sesión. No incluye Kafka, reglas antifraude, notificaciones, Redis, microservicios ni despliegue de producción.

## Documentación

- [Arquitectura actual](docs/architecture.md): topología, módulos activos y límites actuales.
- [Descripción técnica](docs/technical-overview.md): estructura, flujos, persistencia, API, pruebas y limitaciones.
- [Decisiones arquitectónicas (ADR)](docs/adr/): motivos y consecuencias de las decisiones adoptadas.
- [Contexto para una nueva sesión de Codex](docs/codex-context.md): guía para retomar el proyecto desde el estado documentado.
- El roadmap de fases corresponde al apartado 37 del Prompt Maestro proporcionado por el autor; su resumen está en esta página y en la [guía para una nueva sesión de Codex](docs/codex-context.md).

## Fases del Prompt Maestro

| Fase | Alcance | Estado actual |
|---|---|---|
| 0 | Repositorio, README, arquitectura, Docker básico y configuración | Terminada |
| 1 | Spring Boot, PostgreSQL, Flyway, módulos y health endpoint | Terminada |
| 2 | Usuarios, login, JWT, roles y seguridad | Terminada |
| 3 | Creación y consulta de cuentas, estado y saldo | Terminada |
| 4 | Ledger de doble entrada y consistencia | Terminada |
| 5 | Transferencias, validaciones, transacciones y concurrencia | Terminada |
| 6 | Motor de pagos, estados e idempotencia | Terminada; reembolsos pendientes |
| 7 | Frontend: login, dashboard, cuentas, transferencias, pagos y actividad | Terminada; demo de portfolio añadida |
| 8 | Pruebas unitarias, integración, Testcontainers y E2E | Implementada; ejecución completa pendiente de validar con Docker y navegador |
| 9 | Kafka, eventos, productores, consumidores, reintentos y DLQ | Pendiente |
| 10 | Reglas antifraude y procesamiento asíncrono | Pendiente |
| 11 | Notificaciones asíncronas e historial | Pendiente |
| 12 | Redis para caché, idempotencia o rate limiting justificados | Pendiente |
| 13 | Extracción progresiva de microservicios | Pendiente |
| 14 | Docker completo | Pendiente; Compose solo proporciona PostgreSQL |
| 15 | CI/CD | Pendiente |
| 16 | Observabilidad | Pendiente; solo existe Actuator health |
| 17 | AWS | Pendiente |
| 18 | Kubernetes opcional | Pendiente |

El detalle de capacidades implementadas y pendientes, incluida la diferencia entre “código de pruebas presente” y “pruebas ejecutadas con éxito”, está en la [descripción técnica](docs/technical-overview.md).

## Requisitos locales

- Docker Desktop con Docker Compose v2.
- Java 21 y Maven.
- Node.js 20.9 o posterior y npm para el frontend y las pruebas E2E.

Copia `.env.example` como `.env`, configura un `JWT_SECRET` aleatorio de al menos 32 bytes y mantén `.env` fuera de Git. En PowerShell puedes generar la clave así:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Inicia PostgreSQL local:

```powershell
docker compose up -d postgres
```

Compose publica PostgreSQL en `localhost:5433` por defecto; dentro del contenedor escucha en `5432`. El volumen `postgres_data` conserva la base entre reinicios. `docker compose down -v` borra los datos locales de desarrollo.

## Backend

Con PostgreSQL disponible:

```powershell
mvn test
mvn spring-boot:run
```

El health endpoint es `http://localhost:8080/actuator/health`. Las variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` y `JWT_ACCESS_TOKEN_TTL` configuran conexión y JWT. El token dura 15 minutos por defecto. El bootstrap local de usuarios solo se activa con `FINPAY_BOOTSTRAP_ENABLED`; sus valores se controlan mediante variables privadas del `.env`.

## Frontend

En otra terminal, desde la raíz:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

Abre `http://localhost:3000`. Next.js reenvía `/api/*` al backend configurado en `FINPAY_API_URL` (por defecto, `http://localhost:8080`). La interfaz incluye login, resumen, cuentas, transferencias, pagos y actividad. La sesión del prototipo usa `sessionStorage`.

Para ejecutar las pruebas E2E, deja PostgreSQL, el backend con la demo habilitada y el frontend en marcha; después ejecuta desde `frontend`:

```powershell
npx playwright install chromium
npm run test:e2e
```

La suite Maven usa PostgreSQL 16 temporal con Testcontainers. Docker debe estar activo y accesible. Si JUnit no detecta Docker, las pruebas de integración se omiten; revisa los informes antes de interpretar un `mvn test` exitoso como verificación completa.

## Alcance

Los saldos iniciales ordinarios son cero. No hay depósitos/retiros, reservas de fondos, reembolsos ni endpoint general de transacciones. La demo crea dos cuentas EUR aisladas y registra su financiación ficticia en el ledger. Consulta las [limitaciones conocidas](docs/technical-overview.md#limitaciones-conocidas).
