# Descripción técnica de FinPay

Este documento describe lo que implementa el repositorio actualmente. El estado de las fases es el de la tabla de la raíz en [README.md](../README.md); los ADR relacionados están en [`docs/adr`](adr/).

## 1. Estructura del proyecto

```text
FinPay/
├── docs/
│   ├── architecture.md
│   ├── codex-context.md
│   └── adr/                       decisiones arquitectónicas
├── frontend/
│   ├── src/app/                   rutas Next.js y pantallas
│   ├── src/components/            shell y componentes visuales
│   ├── src/lib/                   API, autenticación y tipos del cliente
│   ├── e2e/                       escenarios Playwright
│   └── playwright.config.ts
├── src/main/java/com/finpay/
│   ├── users/                     identidad, autenticación y perfiles
│   ├── security/                  filtros, autorización y JWT
│   ├── accounts/                  cuentas y saldos
│   ├── ledger/                    postings y entradas contables
│   ├── transfers/                 transferencias
│   ├── payments/                  pagos e idempotencia
│   ├── demo/                      sesión de portfolio aislada
│   ├── fraud/                     paquete placeholder
│   ├── notifications/             paquete placeholder
│   └── audit/                     paquete placeholder
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/              Flyway V1–V7
├── src/test/java/com/finpay/      JUnit, MockMvc y Testcontainers
├── docker-compose.yml             PostgreSQL local
└── pom.xml                        Java 21 / Spring Boot Maven
```

En los módulos activos la convención de paquetes expresa intención:

- `api`: controladores REST, DTO de entrada/salida y tratamiento de errores.
- `application`: casos de uso, coordinación de módulos y límites transaccionales.
- `domain`: entidades y reglas/estados del dominio.
- `infrastructure`: repositorios y adaptadores de persistencia.

No todos los módulos tienen todas las carpetas y capas. Por ejemplo, `users` usa repositorios JDBC y `demo` no tiene un modelo de dominio separado. La estructura no representa microservicios.

## 2. Módulos y responsabilidades

### `users`

- `api/AuthController`: registro, login y logout.
- `api/CurrentUserController`: `GET /api/users/me` a partir de claims validados del JWT.
- `application/AuthService`: normalización de email, contraseña, creación `USER`, autenticación y revocación.
- `application/JwtTokenService`: compone y firma claims de acceso.
- `infrastructure/JdbcUserRepository`: usuarios y roles en `finpay.users` y `finpay.user_roles`.
- `infrastructure/JdbcTokenRevocationRepository`: persistencia y consulta de `jti` revocados.
- `infrastructure/LocalUserBootstrap`: creación opcional e idempotente de usuarios locales configurados por entorno.

### `security`

`SecurityConfiguration` desactiva CSRF para esta API stateless, exige bearer JWT para `/api/**` salvo rutas públicas, mapea claims `roles` a autoridades Spring y restringe `/api/admin/**` a `ADMIN`. `JwtConfiguration` configura HS256, emisor, expiración y validación de revocación.

### `accounts`

`Account` es entidad JPA con UUID, propietario, número, moneda, saldo, estado, timestamps y `@Version`. `AccountService` abre la cuenta con saldo cero, valida moneda con `java.util.Currency`, filtra lecturas por `userId` y aplica el cierre solo con saldo cero. La API no permite editar saldo.

### `ledger`

`LedgerService` valida el conjunto de asientos, bloquea las cuentas a afectar, calcula deltas de saldo, escribe `LedgerTransaction` y `LedgerEntry`, y mantiene el saldo en la misma transacción. Las operaciones ordinarias requieren mínimo un débito y un crédito del mismo total, misma moneda, cuentas activas e importes positivos con escala máxima 4. Las referencias son únicas. La base protege las filas ya insertadas con triggers append-only.

Los tipos presentes son `POSTING`, `TRANSFER`, `PAYMENT` y `DEMO_FUNDING`. El caso demo admite una entrada interna sin cuenta cliente y usa el código `DEMO_CAPITAL`.

### `transfers`

`TransferService` valida origen/destino, usuario propietario del origen, estado, moneda, saldo e importe; bloquea ambas cuentas por ID ordenado. Pide al ledger el débito/crédito y persiste la transferencia en una transacción. Los endpoints devuelven historial propio.

### `payments`

`PaymentService` crea pagos, reserva claves idempotentes, gestiona la máquina de estados y captura con ledger. `PaymentRepository` bloquea el pago para transiciones; `PaymentIdempotencyRepository` implementa la reserva mediante JDBC y una restricción única por usuario/clave. `PaymentController` ofrece creación, consulta y una ruta POST por transición.

### `demo`

Con `FINPAY_DEMO_ENABLED=true`, `DemoSessionController` acepta la creación de una sesión y usa `DemoSessionRateLimiter`. `DemoSessionService` crea un usuario aleatorio con rol USER, dos cuentas EUR, una financiación ficticia contable y un JWT normal. La sesión no reutiliza un usuario común ni comparte las cuentas con otro visitante.

### Módulos futuros

`fraud`, `notifications` y `audit` solo contienen metadatos de paquete. No se deben describir como servicios activos ni añadir supuestos de comportamiento a la documentación.

## 3. Petición HTTP, de navegador a base de datos

1. Una página cliente llama al helper `apiRequest` o a las rutas de autenticación.
2. El token actual se añade como header Bearer; Next.js reescribe `/api/:path*` a `FINPAY_API_URL`.
3. La cadena de Spring Security verifica firma y claims del JWT y consulta revocación. Si no se permite la ruta/rol, Spring responde sin invocar el caso de uso.
4. Spring MVC deserializa JSON y evalúa `@Valid` en solicitudes que lo declaran. El controlador deriva el UUID del usuario desde `jwt.getSubject()` en vez de aceptar propietario del cliente.
5. El servicio aplica reglas y, para operaciones de escritura, abre una transacción Spring con PostgreSQL. Un fallo de validación/escritura hace rollback.
6. Spring Data JPA o `JdbcTemplate` persiste datos. El controlador serializa DTO, no expone directamente la representación de entidad como contrato API.

Los errores de cada módulo activo se traducen mediante `@RestControllerAdvice`; la forma concreta del error depende del handler. No existe un contrato de errores central único para todos los módulos.

### Rutas implementadas

| Método y ruta | Acceso y alcance |
|---|---|
| `GET /actuator/health` | Público; health de aplicación/dependencias configurado. |
| `POST /api/auth/register` | Público; crea solo rol `USER`. |
| `POST /api/auth/login` | Público; devuelve access token bearer. |
| `POST /api/auth/logout` | JWT actual; revoca su `jti`. |
| `GET /api/users/me` | JWT; devuelve sujeto, email y roles del token. |
| `POST /api/accounts` | JWT; abre cuenta propia con saldo cero. |
| `GET /api/accounts` | JWT; lista cuentas propias. |
| `GET /api/accounts/{accountId}` | JWT; lee una cuenta propia. |
| `GET /api/accounts/{accountId}/balance` | JWT; lee saldo propio. |
| `PATCH /api/accounts/{accountId}/status` | JWT; cambia estado según las transiciones permitidas. |
| `POST /api/transfers` | JWT; transferencia desde una cuenta propia. |
| `GET /api/transfers` y `GET /api/transfers/{transferId}` | JWT; historial y consulta del usuario iniciador. |
| `POST /api/payments` | JWT más `Idempotency-Key`; crea o devuelve el pago asociado. |
| `GET /api/payments` y `GET /api/payments/{paymentId}` | JWT; solo pagos del usuario. |
| `POST /api/payments/{paymentId}/{submit,authorize,reject,capture,complete}` | JWT; ejecuta una transición permitida por estado. |
| `POST /api/demo/session` | Público solo con `FINPAY_DEMO_ENABLED=true`; crea una sesión aislada con fondos ficticios. |

La regla `/api/admin/**` requiere `ADMIN`, pero no hay rutas administrativas implementadas. No hay endpoint para depósitos, retiros, ledger directo, reembolsos o listado general de transacciones.

## 4. Autenticación y JWT

`POST /api/auth/register` acepta email y contraseña validados y asigna `USER`; no recibe roles para asignación. Email se trimmea y convierte a minúsculas. `DelegatingPasswordEncoder` codifica (BCrypt predeterminado). Login comprueba que la cuenta está habilitada y compara el hash.

Claims documentados por el código: emisor `finpay`, sujeto UUID del usuario, email, lista ordenada de roles, emisión, expiración y `jti` aleatorio. Algoritmo HS256. `JWT_SECRET` debe tener al menos 32 bytes UTF-8. `JWT_ACCESS_TOKEN_TTL` admite duración Spring y por defecto es 15 minutos.

El backend es stateless respecto a sesiones, pero logout requiere estado de revocación: `POST /api/auth/logout` guarda `(jti, expires_at)` y el validador consulta PostgreSQL en cada request autenticada. La consulta ignora tokens revocados que ya expiraron, pero no hay tarea implementada para purgar sus filas.

Rutas públicas explícitas: `GET /actuator/health`, `POST /api/auth/register`, `POST /api/auth/login` y `POST /api/demo/session` solo cuando el controlador demo está activado. Toda otra ruta `/api/**` requiere autenticación; otros paths se deniegan. La regla ADMIN existe pero no hay endpoints `/api/admin/**` actuales.

## 5. JPA, Hibernate, Spring Data y JDBC

El proyecto incluye Spring Data JPA y JDBC. No todo el acceso a datos es JPA:

| Datos/operación | Acceso actual |
|---|---|
| Accounts | JPA `AccountRepository` |
| Ledger | JPA `LedgerTransactionRepository` y entidades con entradas asociadas |
| Transfers | JPA `TransferRepository` |
| Payments | JPA `PaymentRepository` |
| Users, roles y bootstrap | JDBC `JdbcUserRepository` |
| Revocación JWT | JDBC `JdbcTokenRevocationRepository` |
| Claves de idempotencia de pago | JDBC `PaymentIdempotencyRepository` |

Hibernate mapea entidades y verifica columnas usando `ddl-auto=validate`; no genera migraciones. Spring Data crea implementaciones de interfaces de repositorio. `@Version` existe en cuenta y pago para detectar escrituras optimistas; para transferencias/capturas y transiciones críticas también se usan locks pesimistas `PESSIMISTIC_WRITE` explícitos.

La configuración de transacciones `@Transactional` se aplica a nivel de aplicación. La persistencia JPA y JDBC participa en el mismo `DataSource`/transaction manager de Spring, lo que permite que idempotencia, registro de pago y ledger se confirmen o reviertan juntos.

## 6. PostgreSQL y Flyway

Compose define un solo servicio `postgres` con volumen `postgres_data`, healthcheck y puerto host configurable (`POSTGRES_PORT`, 5433 por defecto). El esquema de negocio es `finpay`. `application.properties` importa opcionalmente `.env` como archivo de propiedades; no se debe versionar `.env`.

Migraciones actuales:

| Versión | Cambio |
|---|---|
| V1 | Crea el esquema `finpay`. |
| V2 | Usuarios, roles y tabla de revocación. |
| V3 | Cuentas, estado, saldo, versión e índices de propietario. |
| V4 | Ledger, entradas, restricciones e inmutabilidad append-only. |
| V5 | Transferencias y relación a transacción del ledger. |
| V6 | Pagos y claves idempotentes. |
| V7 | Cuenta contraparte de ledger para financiación demo. |

Flyway crea/actualiza el esquema al iniciar. Hibernate compara el modelo ORM contra ese esquema. Migraciones que ya se aplicaron no se editan: se añade una nueva versión.

## 7. Cuentas y contabilidad

Las cuentas admiten moneda aceptada por `Currency.getInstance`, un saldo `NUMERIC(19,4)`, y estados `ACTIVE`, `BLOCKED`, `CLOSED`. Se crean en cero. El cierre requiere saldo cero y es terminal. El propietario se determina a partir del usuario del token.

El ledger registra entradas append-only, con importe positivo y dirección `DEBIT` o `CREDIT`. Las reglas globales del posting se validan en la aplicación: número mínimo de entradas, lado de débito y crédito no vacío, suma igual, una entrada por cuenta, misma moneda y saldo final no negativo. PostgreSQL aplica constraints por fila/relación y triggers de no mutación; el total balanceado depende de pasar por el servicio de ledger.

En financiación demo el dinero es ficticio. Se crea un crédito para la cuenta EUR de la sesión y una contrapartida de débito llamada `DEMO_CAPITAL`, sin abrir una cuenta normal ni exponer un endpoint de ingreso.

## 8. Transferencias

API:

| Método y ruta | Comportamiento |
|---|---|
| `POST /api/transfers` | Crea una transferencia. El origen debe ser del usuario; destino activo, moneda igual; importe positivo y escala máxima 4. |
| `GET /api/transfers` | Lista operaciones iniciadas por el usuario. |
| `GET /api/transfers/{transferId}` | Lee una operación del usuario. |

El servicio bloquea las dos cuentas en orden UUID para evitar deadlocks por orden inverso y carreras de saldo. En una transacción escribe posting `TRANSFER`, actualiza saldos y guarda el recurso. No se permite autopago a la misma cuenta ni saldo negativo. No existe idempotency key para transferencias.

## 9. Payments e idempotencia

API:

| Método y ruta | Comportamiento |
|---|---|
| `POST /api/payments` | Crea/reproduce un pago; requiere `Idempotency-Key`. |
| `GET /api/payments` | Lista los pagos del usuario. |
| `GET /api/payments/{paymentId}` | Consulta un pago del usuario. |
| `POST /api/payments/{paymentId}/submit` | `CREATED` a `PENDING`. |
| `POST /api/payments/{paymentId}/authorize` | Valida cuentas/saldo, pasa a `AUTHORIZED` o `REJECTED`. |
| `POST /api/payments/{paymentId}/reject` | Rechaza estado permitido por el dominio. |
| `POST /api/payments/{paymentId}/capture` | Vuelve a validar; captura en ledger o pasa a `FAILED`. |
| `POST /api/payments/{paymentId}/complete` | `CAPTURED` a `COMPLETED`. |

La clave se normaliza (trim, entre 1 y 128 caracteres) y se guarda junto con usuario, SHA-256 de `sourceAccountId|destinationAccountId|amount` y payment UUID. `INSERT ... ON CONFLICT DO NOTHING` junto a clave primaria `(user_id, idempotency_key)` serializa peticiones concurrentes. Misma huella responde el recurso previamente creado; otra huella da conflicto.

Importes deben ser positivos, máximo cuatro decimales y precisión máxima 19. El origen pertenece al usuario; las cuentas deben existir, estar activas y usar misma moneda cuando se autoriza/captura. La autorización no retiene saldo y no garantiza captura futura. `REFUNDED` está en el enum/restricción pero sin transición pública o contabilización.

## 10. Frontend

Next.js App Router presenta `/login`, `/dashboard`, `/accounts`, `/transfers`, `/payments`, `/transactions`. El dashboard consulta cuentas, transferencias y pagos mediante TanStack Query. El shell consulta `/api/users/me`, protege la interfaz cuando no hay token y reacciona a 401.

El token y el marcador `finpay_demo` viven en `sessionStorage`, no en una cookie HttpOnly. El navegador añade el Bearer token a llamadas protegidas. Logout quita primero el estado local y luego intenta `/api/auth/logout`; si la llamada falla, la interfaz permanece cerrada, pero no puede revocar un token offline.

La pantalla de actividad combina la respuesta de pagos y transferencias en cliente, no representa un ledger feed completo. La demo puede iniciar sesión directamente desde el botón en la página de login. El proyecto no promete compatibilidad con navegadores distintos al navegador usado al validar Playwright.

## 11. Pruebas y ejecución

Comandos backend desde la raíz:

```powershell
mvn test
mvn spring-boot:run
```

Comandos frontend desde `frontend/`:

```powershell
npm install
npm run lint
npm run build
npx playwright install chromium
npm run test:e2e
```

Las pruebas unitarias ejercitan la máquina de estados de `Payment`. Las pruebas de integración usan `@SpringBootTest`, MockMvc y PostgreSQL temporal mediante Testcontainers. Cuando el daemon Docker no es detectado, `disabledWithoutDocker=true` omite clases de integración; comprobar el exit code Maven no es suficiente: inspecciona `target/surefire-reports` y el conteo skipped. Playwright requiere PostgreSQL y backend con demo habilitada, además del servidor Next.

Último estado de verificación conocido: `mvn test` terminó con las 3 unitarias aprobadas y 24 integraciones omitidas por acceso a Docker; las 2 pruebas Playwright fueron enumeradas pero no ejecutadas. Lint y build de frontend terminaron correctamente.

## 12. Límites y trabajo pendiente

- Nada procesa dinero real ni integra redes de pago externas.
- No hay depósitos/retiros, reserva de fondos ni refunds. Los saldos normales solo cambian con transferencias/pagos registrados por ledger.
- No hay endpoint de transacciones general ni paginación implementada/documentada para los historiales.
- Transferencias carecen de idempotencia. En pagos, solo la creación tiene clave idempotente.
- `REFUNDED` no se puede alcanzar mediante la API actual.
- El rate limiter demo es local al proceso y la sesión demo no se purga automáticamente.
- La revocación consulta la base en cada petición y los tokens expirados no tienen limpieza periódica implementada.
- No hay endpoints administrativos, aunque la regla de rol está configurada.
- Fraude, notificaciones y auditoría son placeholders. Kafka y Redis todavía no forman parte de la aplicación.
- Compose solo cubre PostgreSQL; falta empaquetado Docker de la aplicación, CI/CD, configuración completa de observabilidad, AWS y Kubernetes.
- La Fase 8 está codificada, pero las últimas pruebas de integración/E2E no se validaron ejecutándose de punta a punta.
- El frontend declara varias dependencias como `latest`; aunque exista lockfile, futuras instalaciones que lo actualicen pueden cambiar la resolución.
