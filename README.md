# FinPay

FinPay es una simulación de una plataforma bancaria y de pagos, creada como proyecto de portfolio para practicar decisiones de ingeniería de software. No procesa dinero real.

## Estado

El proyecto comienza como un monolito modular. La Fase 0 preparó el entorno local. La Fase 1 añadió Spring Boot, PostgreSQL, Flyway y un endpoint de salud. La Fase 2 incorpora registro, login, JWT, roles y protección de rutas.

## Arquitectura inicial

La aplicación se organizará por módulos de negocio dentro de un único despliegue: `users`, `accounts`, `payments`, `transfers`, `ledger`, `fraud`, `notifications` y `audit`. Los módulos compartirán PostgreSQL al inicio y mantendrán límites explícitos. No se introducirán microservicios ni mensajería hasta que exista una necesidad concreta.

PostgreSQL es la base de datos principal. Docker Compose proporciona una instancia local con volumen persistente. Flyway gestiona el esquema `finpay`. Las credenciales predeterminadas de Compose son exclusivamente para desarrollo local y no deben reutilizarse fuera de ese entorno.

Consulta [la descripción de arquitectura](docs/architecture.md) y el [ADR-001](docs/adr/ADR-001-modular-monolith.md) para conocer las decisiones iniciales.

## Requisitos

- Docker Desktop con Docker Compose v2.
- Java 21 y Maven para las fases que incorporen el backend.

## Entorno local

Copia `.env.example` a `.env` y configura `JWT_SECRET` con un valor aleatorio de al menos 32 bytes. En PowerShell puedes generar uno con:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Pega el resultado en `.env`. Ese archivo está excluido de Git; no guardes el secreto en el repositorio.

Inicia PostgreSQL:

```bash
docker compose up -d postgres
```

Comprueba el estado:

```bash
docker compose ps
```

Detén el servicio conservando los datos:

```bash
docker compose down
```

Para eliminar también los datos persistidos de desarrollo:

```bash
docker compose down -v
```

La base de FinPay queda disponible en `localhost:5433` por defecto, con base de datos y usuario `finpay`. El puerto del contenedor sigue siendo `5432`; se usa `5433` en el host para evitar conflictos con instalaciones locales de PostgreSQL.

## Backend

Se requiere Java 21 y Maven. Con PostgreSQL iniciado, ejecuta las pruebas:

```bash
mvn test
```

Inicia la aplicación:

```bash
mvn spring-boot:run
```

El endpoint de salud está disponible en `http://localhost:8080/actuator/health`. La conexión JDBC, la clave JWT y su vigencia pueden configurarse mediante `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` y `JWT_ACCESS_TOKEN_TTL` (por defecto, 15 minutos).

## Identidad y seguridad

- `POST /api/auth/register`: crea un usuario con rol `USER`.
- `POST /api/auth/login`: valida email y contraseña y devuelve un JWT de acceso.
- `POST /api/auth/logout`: revoca el JWT actual en PostgreSQL.
- `GET /api/users/me`: devuelve la identidad y roles del token.
- `/api/admin/**` requiere rol `ADMIN`; el registro público nunca permite asignarlo.

Las contraseñas se almacenan con el `DelegatingPasswordEncoder` de Spring Security (BCrypt por defecto). Los JWT usan HS256, tienen expiración y un identificador único; el backend comprueba la revocación en PostgreSQL. `ADMIN` debe asignarse por un proceso administrativo controlado.

## Roadmap

1. Preparación del repositorio y del entorno local.
2. Backend inicial con Spring Boot, PostgreSQL, Flyway y health endpoint (implementada).
3. Identidad, usuarios y seguridad JWT (implementada).
4. Cuentas y ledger (implementada).
5. Transferencias (implementada).
6. Payments (implementada), frontend y fases posteriores.

El detalle de cada fase se irá incorporando cuando se implemente; esta base no incluye todavía código de aplicación.

## Cuentas

- `POST /api/accounts`: crea una cuenta en una moneda ISO 4217 soportada, con saldo inicial cero.
- `GET /api/accounts` y `GET /api/accounts/{id}`: lista o consulta las cuentas propias.
- `GET /api/accounts/{id}/balance`: consulta el saldo de una cuenta propia.
- `PATCH /api/accounts/{id}/status`: cambia el estado entre `ACTIVE`, `BLOCKED` y `CLOSED`. `CLOSED` es terminal.

Todas las rutas requieren JWT. El saldo no puede editarse directamente; se actualizará mediante los movimientos del ledger en una fase posterior. La Fase 3 incorpora persistencia JPA con validación del esquema Flyway y bloqueo optimista.

## Ledger

La Fase 4 añade el ledger de doble entrada. Cada registro interno requiere al menos un débito y un crédito, ambos totales deben coincidir, las cuentas deben compartir moneda y el débito no puede dejar saldo negativo. Las entradas y transacciones son append-only en PostgreSQL. El saldo de cuenta se actualiza en la misma transacción que el asiento. No hay endpoint público de posting todavía; las operaciones visibles para el usuario llegarán con transferencias en la Fase 5.

## Transferencias

- `POST /api/transfers`: transfiere un importe positivo entre dos cuentas activas de la misma moneda. El usuario debe ser propietario de la cuenta de origen.
- `GET /api/transfers` y `GET /api/transfers/{id}`: consulta el historial de transferencias iniciadas por el usuario.

La transferencia, los dos asientos del ledger y la actualización de ambos saldos se confirman en una sola transacción PostgreSQL. Las cuentas se bloquean en orden estable para impedir que solicitudes concurrentes gasten dos veces el mismo saldo. Los pagos usan claves de idempotencia propias en la Fase 6.

## Payments

`POST /api/payments` requiere el encabezado `Idempotency-Key`. La clave queda asociada al usuario y a una huella de la solicitud en PostgreSQL: repetir la misma petición devuelve el mismo pago, mientras que reutilizar la clave con datos distintos da un conflicto.

Flujo de estados: `CREATED` → `PENDING` → `AUTHORIZED` → `CAPTURED` → `COMPLETED`. Se puede rechazar un pago pendiente; si el saldo deja de estar disponible antes de capturarlo, queda `FAILED`. Las acciones están disponibles en `/api/payments/{id}/submit`, `/authorize`, `/reject`, `/capture` y `/complete`. El historial solo muestra pagos del usuario autenticado. Los reembolsos se reservan para una fase posterior.

La captura registra dos asientos `PAYMENT` y actualiza los saldos en una sola transacción. La autorización comprueba fondos, pero no los reserva; por eso la captura vuelve a comprobarlos bajo bloqueo de las cuentas.

## Interfaz web (Fase 7)

La interfaz requiere Node.js 20.9 o posterior y npm. Con PostgreSQL iniciado, ejecuta el backend en una terminal (`mvn spring-boot:run`) y, desde la raíz del repositorio, inicia el frontend:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

Abre `http://localhost:3000` e inicia sesión con un usuario previamente registrado mediante el API. Next.js reenvía `/api/*` al backend; configura `FINPAY_API_URL` en `frontend/.env.local` si el backend no está en `http://localhost:8080`. La sesión del prototipo se guarda en `sessionStorage` y se elimina al cerrar sesión o cerrar la pestaña.

La interfaz incluye resumen, cuentas, transferencias, pagos y actividad. Las cuentas nuevas empiezan con saldo cero y el backend aún no tiene una operación para ingresar fondos. Para transferencias y pagos, el destino se introduce con el UUID de una cuenta. La actividad combina los historiales de pagos y transferencias porque aún no existe un endpoint general de transacciones. Las acciones de pago permiten avanzar manualmente por sus estados.

La Fase 7 del roadmap queda implementada. Las fases posteriores pueden ampliar el producto con funciones administrativas, fraude, notificaciones, auditoría y operaciones adicionales.

### Usuarios de demostración

Al iniciar el backend con la configuración local de `.env`, se crean de forma idempotente estos usuarios si todavía no existen:

| Rol | Email | Contraseña |
|---|---|---|
| Administrador (`ADMIN`) | `admin@finpay.local` | `FinPayAdmin2026!` |
| Usuario (`USER`) | `user@finpay.local` | `FinPayUser2026!` |

Puedes usar cualquiera para entrar en `http://localhost:3000`. Las contraseñas se almacenan con BCrypt. El inicializador no cambia cuentas ya existentes y el registro público sigue creando únicamente usuarios `USER`. La activación está en `.env` mediante `FINPAY_BOOTSTRAP_ENABLED`; debe permanecer desactivada fuera del entorno local. Estas credenciales son exclusivamente de demostración.
