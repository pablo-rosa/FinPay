# Arquitectura actual de FinPay

## Topología

FinPay es un monolito modular: un backend Spring Boot expone la API y coordina casos de uso; PostgreSQL conserva el estado; Next.js ofrece la interfaz web y reescribe `/api/*` al backend. Docker Compose configura PostgreSQL para desarrollo. No hay despliegues independientes por módulo.

```text
Navegador
   │ bearer JWT
   ▼
Next.js ── /api/* ──► Spring Boot
                           │
                Spring Security / MVC
                           │
               módulos de aplicación
                           │
            JPA + JdbcTemplate / JDBC
                           │
             PostgreSQL · esquema finpay
                     ▲
                   Flyway
```

Las operaciones de negocio y sus entradas de ledger comparten transacciones PostgreSQL. Esto mantiene transferencias y capturas atómicas dentro del mismo proceso y base de datos.

## Módulos presentes

Los módulos del backend están en `src/main/java/com/finpay`. Los activos separan, según el caso, `api`, `application`, `domain` e `infrastructure`; no todas las carpetas existen en cada módulo.

| Módulo | Estado actual |
|---|---|
| `users` | Registro, login, perfil del token, roles y revocación JWT. Usuarios, roles y revocaciones usan JDBC. Incluye bootstrap local opcional. |
| `security` | Cadena stateless de Spring Security, autorización por ruta/rol y configuración HS256. |
| `accounts` | Creación con saldo cero, consulta propia, estados y entidad JPA versionada. |
| `ledger` | Validación de postings, doble partida, bloqueo de cuentas, actualización de balances e historial append-only. |
| `transfers` | Transferencias atómicas desde cuentas propias a otra cuenta activa de igual moneda. |
| `payments` | Creación idempotente por usuario/clave, máquina de estados y captura en el ledger. Reembolsos pendientes. |
| `demo` | Usuario y cuentas aislados por sesión, fondos ficticios contabilizados y limitación local por IP. |
| `fraud`, `notifications`, `audit` | Solo placeholders de paquete; no implementan casos de uso. |

## Límites de módulos y responsabilidades

- Los controladores reciben DTO y llaman a servicios; no deben implementar reglas contables.
- `users` emite tokens; `security` verifica esos tokens y protege rutas. Los demás módulos derivan identidad desde el JWT validado.
- `accounts` es dueño de cuentas y estados, pero no tiene API para modificar directamente el saldo.
- `ledger` es invocado por transferencias, capturas de pago y el aprovisionamiento de la demo. No existe endpoint público de posting.
- `transfers` y `payments` coordinan sus datos de negocio con el ledger dentro de una transacción local.
- `fraud`, `notifications` y `audit` no participan aún en estos flujos.

## Flujo de una petición

El navegador añade `Authorization: Bearer ...` a llamadas protegidas; Next.js reescribe la ruta al backend. Spring Security comprueba autenticidad, expiración, emisor y revocación del JWT, y aplica la regla de acceso. Spring MVC deserializa/valida el DTO y llama al controlador. El servicio ejecuta reglas bajo `@Transactional` cuando hay escritura, accede mediante JPA o JDBC y devuelve el resultado como DTO. Los handlers REST de cada módulo traducen errores a respuestas HTTP.

Health, registro, login y (cuando está habilitada) creación demo son rutas públicas. El resto de `/api/**` requiere autenticación; `/api/admin/**` requiere rol `ADMIN`, pero no hay controladores de administración. Otros paths se deniegan.

## Seguridad y datos

JWT HS256 contiene `iss=finpay`, sujeto UUID, email, roles, timestamps y `jti`. El secreto `JWT_SECRET` debe tener 32 bytes UTF-8 como mínimo; TTL predeterminado 15 minutos. Logout persiste el `jti` hasta expiración y el decoder consulta su revocación en PostgreSQL en cada request autenticada. Los passwords usan `DelegatingPasswordEncoder` (BCrypt predeterminado). El registro público asigna solo `USER`.

Flyway administra el esquema `finpay` con migraciones V1–V7. Hibernate usa `ddl-auto=validate`: comprueba el modelo, pero Flyway es dueño del DDL. JPA/Spring Data se usa para cuentas, ledger, transferencias y pagos; JDBC para usuarios, roles, revocación y claves idempotentes. Las entidades `Account` y `Payment` incluyen `@Version`; las operaciones financieras críticas también toman locks `PESSIMISTIC_WRITE` y ordenan locks de cuenta por UUID.

Compose usa PostgreSQL 17 Alpine y publica `5433:5432` por defecto. Testcontainers configura PostgreSQL 16 Alpine. Las contraseñas de entorno local y secretos no deben publicarse; `.env` está excluido de Git.

## Integridad financiera

Los importes usan `BigDecimal` y precisión `NUMERIC(19,4)`. Los asientos del ledger deben ser positivos, balanceados, de moneda común y no dejar saldo negativo. Los triggers de PostgreSQL prohíben actualizar o borrar entradas/transacciones del ledger; el balance global se valida en `LedgerService`, no por un constraint diferido en la base.

Las transferencias bloquean origen y destino y confirman posting, cambios de saldo y transferencia como una unidad. La creación de pagos usa idempotencia `(user_id, idempotency_key)` con huella SHA-256; solo protege creación de pagos, no sus transiciones ni transferencias. La autorización de pago no reserva saldo; la captura vuelve a validar cuentas y fondos.

La demo usa un caso contable específico: acredita una cuenta de visitante con fondos ficticios y debita la contrapartida `DEMO_CAPITAL`. No es un endpoint normal de ingreso ni una cuenta bancaria externa.

## Evolución y decisiones

La extracción de servicios requiere límites estables y una necesidad técnica observable; no se asume una migración automática a microservicios. Kafka, Redis, fraude y notificaciones siguen fuera del sistema activo.

- [ADR-001: monolito modular](adr/ADR-001-modular-monolith.md)
- [ADR-002: JWT](adr/ADR-002-jwt-authentication.md)
- [ADR-003: cuentas](adr/ADR-003-accounts.md)
- [ADR-004: ledger](adr/ADR-004-double-entry-ledger.md)
- [ADR-005: transferencias atómicas](adr/ADR-005-atomic-transfers.md)
- [ADR-006: pagos e idempotencia](adr/ADR-006-payment-state-and-idempotency.md)
- [ADR-007: demo aislada](adr/ADR-007-isolated-portfolio-demo.md)
- [ADR-008: estrategia de pruebas](adr/ADR-008-testing-strategy.md)

La [descripción técnica](technical-overview.md) contiene estructura de archivos, rutas API, módulos detallados, persistencia, interfaz, pruebas y limitaciones conocidas. El [README](../README.md) resume las fases 0–18 del Prompt Maestro y su estado.
