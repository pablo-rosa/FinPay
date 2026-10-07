# FINPAY — PROMPT MAESTRO DE DESARROLLO

## 1. CONTEXTO

Quiero desarrollar desde cero una aplicación llamada **FinPay**, una plataforma bancaria/pagos ficticia cuyo objetivo principal es demostrar capacidades de ingeniería de software de nivel profesional.

Este proyecto será utilizado como portfolio para demostrar conocimientos de:

- Java 21
- Spring Boot
- Spring Security
- PostgreSQL
- Next.js
- TypeScript
- Redis
- Apache Kafka
- Arquitectura modular
- Arquitectura orientada a eventos
- Microservicios
- Testing
- Docker
- CI/CD
- Observabilidad
- AWS

IMPORTANTE:

No quiero simplemente una aplicación CRUD.

Quiero construir el proyecto de forma progresiva, comenzando con un **monolito modular bien diseñado** y evolucionándolo posteriormente hacia una arquitectura distribuida.

El proyecto debe estar diseñado para poder explicar en una entrevista:

- por qué se tomó cada decisión arquitectónica
- qué problemas resuelve cada tecnología
- cuándo tiene sentido introducir Kafka
- cuándo tiene sentido separar servicios
- cómo se gestionan transacciones
- cómo se evita procesar dos veces una operación
- cómo se controla la concurrencia
- cómo se prueban sistemas distribuidos

---

# 2. REGLA PRINCIPAL DE DESARROLLO

NO intentes implementar toda la aplicación de una sola vez.

Debes trabajar por fases pequeñas y verificables.

Antes de comenzar cada fase:

1. Explica brevemente qué vamos a construir.
2. Explica qué archivos/componentes vas a crear o modificar.
3. Implementa la fase.
4. Ejecuta las pruebas.
5. Corrige los errores.
6. Comprueba que la aplicación sigue funcionando.
7. Resume qué se ha conseguido.
8. Propón la siguiente fase.

NO avances automáticamente a la siguiente fase si la actual no funciona correctamente.

---

# 3. STACK PRINCIPAL

## Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- JWT
- Bean Validation
- PostgreSQL
- Redis
- Apache Kafka
- JUnit 5
- Mockito
- Testcontainers

## Frontend

- Next.js
- TypeScript
- React
- Tailwind CSS
- React Query/TanStack Query cuando tenga sentido

## Infraestructura

- Docker
- Docker Compose
- GitHub Actions

## Observabilidad

Posteriormente:

- OpenTelemetry
- Prometheus
- Grafana

## Cloud

Posteriormente:

- AWS

Kubernetes es opcional y deberá implementarse solamente después de que el resto de la aplicación esté correctamente terminada.

---

# 4. ARQUITECTURA EVOLUTIVA

La arquitectura debe evolucionar de esta forma:

FASE 1:

Modular Monolith

```text
finpay
├── users
├── accounts
├── payments
├── transfers
├── ledger
├── fraud
├── notifications
└── audit
```

Posteriormente, cuando existan razones técnicas claras, algunos módulos se convertirán en microservicios.

Arquitectura final aproximada:

```text
                    ┌───────────────┐
                    │   Next.js     │
                    │   Frontend    │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ API / Gateway │
                    └───────┬───────┘
                            │
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
        Accounts         Payments        Transfers
             │              │              │
             └──────────────┼──────────────┘
                            │
                            ▼
                         Kafka
                            │
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
          Ledger          Fraud       Notifications
```

No crear decenas de microservicios artificialmente.

Extraer únicamente aquellos módulos que tengan una justificación clara.

---

# 5. FUNCIONALIDAD PRINCIPAL

FinPay debe permitir simular una plataforma financiera.

IMPORTANTE:

No utilizar dinero real.

Todo será una simulación.

---

# 6. USUARIOS

Implementar:

- registro
- login
- logout
- JWT
- roles
- perfil de usuario
- actualización de datos
- activación/desactivación
- control de acceso

Roles iniciales:

```text
USER
ADMIN
```

Posteriormente se podrán añadir:

```text
SUPPORT
AUDITOR
```

---

# 7. CUENTAS

Un usuario puede tener una o varias cuentas.

Una cuenta debe tener:

```text
id
userId
accountNumber
currency
balance
status
createdAt
updatedAt
version
```

Estados:

```text
ACTIVE
BLOCKED
CLOSED
```

Utilizar optimistic locking donde tenga sentido.

Ejemplo:

```java
@Version
private Long version;
```

Evitar problemas de concurrencia cuando varias operaciones intenten modificar simultáneamente el saldo.

---

# 8. LEDGER

Esta es una parte MUY IMPORTANTE del proyecto.

No confiar únicamente en:

```text
account.balance
```

para representar el historial financiero.

Implementar un sistema de ledger basado en **double-entry bookkeeping**.

Cada operación financiera debe generar movimientos contables.

Ejemplo:

```text
Cuenta A
-100 EUR

Cuenta B
+100 EUR
```

El sistema debe garantizar que cada operación tenga sus correspondientes entradas.

Diseñar:

```text
LedgerTransaction
LedgerEntry
```

Una posible estructura:

```text
LedgerTransaction
├── id
├── reference
├── type
├── status
├── createdAt
└── entries

LedgerEntry
├── id
├── transactionId
├── accountId
├── amount
├── direction
└── createdAt
```

Las operaciones deben mantener consistencia.

---

# 9. TRANSFERENCIAS

Implementar transferencias entre cuentas.

Ejemplo:

```text
Cuenta A
1000 EUR

Transferencia
200 EUR

Cuenta B
500 EUR
```

Resultado:

```text
Cuenta A = 800 EUR
Cuenta B = 700 EUR
```

Validaciones:

- cuenta origen existe
- cuenta destino existe
- cuentas activas
- saldo suficiente
- importe positivo
- cuenta origen != cuenta destino

Implementar transacciones de base de datos correctamente.

---

# 10. IDEMPOTENCIA

Las operaciones financieras deben ser idempotentes.

Por ejemplo:

```http
POST /api/payments
Idempotency-Key: abc-123
```

Si el cliente envía la misma operación dos veces con la misma clave:

```text
abc-123
```

no se debe ejecutar dos veces.

Implementar inicialmente utilizando PostgreSQL.

Posteriormente evaluar Redis para determinados casos.

---

# 11. PAYMENTS

Crear un Payment Engine.

Un pago debe tener estados:

```text
CREATED
PENDING
AUTHORIZED
REJECTED
CAPTURED
COMPLETED
REFUNDED
FAILED
```

Implementar una máquina de estados.

No permitir transiciones inválidas.

Ejemplo:

```text
CREATED
   ↓
PENDING
   ↓
AUTHORIZED
   ↓
CAPTURED
   ↓
COMPLETED
```

Un pago rechazado no debe poder pasar directamente a:

```text
COMPLETED
```

---

# 12. EVENTOS

Una vez que el núcleo transaccional funcione correctamente, introducir Apache Kafka.

IMPORTANTE:

Kafka NO debe introducirse desde el primer día.

Primero debe funcionar correctamente la lógica de negocio.

Después crear eventos como:

```text
PaymentCreated
PaymentAuthorized
PaymentRejected
PaymentCaptured
PaymentCompleted
PaymentRefunded

TransferCreated
TransferCompleted
TransferRejected

FraudCheckRequested
FraudCheckCompleted

NotificationRequested
```

Ejemplo conceptual:

```text
Payment Service
       │
       ▼
Kafka topic: payments
       │
       ├──────────► Fraud Service
       │
       ├──────────► Ledger Service
       │
       └──────────► Notification Service
```

Los eventos deben ser objetos bien definidos.

Ejemplo:

```json
{
  "eventId": "uuid",
  "eventType": "PaymentCompleted",
  "occurredAt": "...",
  "paymentId": "uuid",
  "amount": 100,
  "currency": "EUR"
}
```

Utilizar inicialmente JSON.

Diseñar los eventos para que posteriormente sea posible migrar a Avro o Protobuf si fuera necesario.

---

# 13. KAFKA

Implementar:

- producers
- consumers
- topics
- consumer groups
- partitions
- offsets
- retries
- error handling
- dead-letter topic cuando tenga sentido

Explicar en documentación:

- qué problema resuelve Kafka
- por qué no utilizar llamadas REST para todo
- diferencia entre comunicación síncrona y asíncrona
- qué ocurre si un consumidor falla
- qué ocurre si un mensaje se procesa dos veces

Implementar consumidores idempotentes.

---

# 14. FRAUD

Crear un sistema de detección de fraude sencillo basado en reglas.

Ejemplos:

```text
- demasiadas operaciones en poco tiempo
- importe demasiado elevado
- comportamiento sospechoso
- demasiados intentos fallidos
```

No utilizar machine learning inicialmente.

Debe ser un sistema basado en reglas para demostrar arquitectura.

Posteriormente podría evolucionarse.

---

# 15. REDIS

Introducir Redis después de tener una aplicación funcional.

Utilizarlo para casos donde realmente tenga sentido:

- cache
- idempotency
- rate limiting
- información temporal

No utilizar Redis simplemente porque forma parte del stack.

Documentar por qué se utiliza en cada caso.

---

# 16. NOTIFICACIONES

Crear un sistema de notificaciones.

Tipos:

```text
EMAIL
PUSH
IN_APP
```

No es necesario enviar emails reales inicialmente.

Crear un sistema simulado.

Ejemplo:

```text
PaymentCompleted
       ↓
Kafka
       ↓
Notification Service
       ↓
Notification created
```

---

# 17. AUDIT LOG

Registrar operaciones importantes:

```text
LOGIN
PAYMENT_CREATED
PAYMENT_COMPLETED
TRANSFER_CREATED
TRANSFER_COMPLETED
ACCOUNT_BLOCKED
USER_UPDATED
```

Guardar:

```text
id
userId
action
resource
resourceId
timestamp
metadata
ipAddress
```

Los audit logs deben ser append-only.

---

# 18. LIMITES

Implementar límites de operaciones.

Ejemplo:

```text
dailyTransferLimit
dailyPaymentLimit
maximumTransactionAmount
```

El sistema debe impedir operaciones que superen los límites.

---

# 19. REFUNDS

Implementar reembolsos.

Ejemplo:

```text
COMPLETED
   ↓
REFUNDED
```

Validar:

- pago existente
- pago completado
- no reembolsado previamente
- importe válido

Crear el correspondiente movimiento en el ledger.

---

# 20. RECONCILIACIÓN

Crear posteriormente un proceso de reconciliación.

El sistema debe poder comprobar:

```text
balance de cuentas
vs
movimientos del ledger
```

Si existe una inconsistencia, debe detectarla.

Crear un proceso que genere información como:

```text
ReconciliationResult
```

con:

```text
status
differences
timestamp
```

---

# 21. API

Utilizar REST.

Convenciones:

```text
/api/auth
/api/users
/api/accounts
/api/transfers
/api/payments
/api/transactions
/api/admin
```

Utilizar DTOs.

NO exponer directamente entidades JPA.

Ejemplo:

```text
Entity
↓
Service
↓
DTO
↓
Controller
```

---

# 22. ERRORES

Crear un formato de error consistente.

Ejemplo:

```json
{
  "timestamp": "...",
  "status": 400,
  "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient funds",
  "path": "/api/transfers"
}
```

Crear manejo global de excepciones.

Utilizar:

```java
@RestControllerAdvice
```

---

# 23. VALIDACIÓN

Utilizar Bean Validation.

Ejemplo:

```java
@NotNull
@NotBlank
@Positive
@Email
@Size
```

No confiar únicamente en las validaciones del frontend.

Las reglas críticas deben existir en backend.

---

# 24. SEGURIDAD

Implementar:

- Spring Security
- JWT
- password hashing
- roles
- authorization
- endpoint protection

Nunca almacenar passwords en texto plano.

Nunca incluir secretos en Git.

Utilizar variables de entorno.

---

# 25. FRONTEND

Crear un dashboard profesional utilizando:

- Next.js
- TypeScript
- Tailwind CSS

Páginas:

```text
/login
/register
/dashboard
/accounts
/accounts/[id]
/payments
/payments/[id]
/transfers
/transactions
/profile
/settings
/admin
```

Dashboard:

```text
Balance total
Últimas transacciones
Pagos recientes
Transferencias
Estado de cuentas
Alertas
```

No crear únicamente una UI bonita.

La interfaz debe consumir realmente la API.

---

# 26. TESTING

El testing es una parte fundamental.

Backend:

- unit tests
- integration tests
- repository tests
- controller tests
- service tests
- security tests

Utilizar:

```text
JUnit 5
Mockito
Testcontainers
```

Para integración utilizar PostgreSQL real mediante Testcontainers.

Posteriormente probar Kafka con Testcontainers cuando el sistema lo utilice.

Frontend:

- tests relevantes
- Playwright para E2E

Casos importantes:

```text
registro
login
transferencia
pago
saldo insuficiente
idempotencia
concurrencia
permisos
refund
```

---

# 27. CONCURRENCIA

Este proyecto debe demostrar que comprendo problemas de concurrencia.

Ejemplo:

Dos operaciones intentan gastar simultáneamente el mismo saldo.

El sistema no debe permitir:

```text
Balance = 100

Request A → -100
Request B → -100

Resultado inválido = -100
```

Investigar e implementar correctamente:

- optimistic locking
- transacciones
- aislamiento
- constraints
- locking cuando sea necesario

No utilizar soluciones artificiales sin explicar por qué.

---

# 28. DOCKER

Crear Dockerfiles para los componentes necesarios.

Crear:

```text
docker-compose.yml
```

para ejecutar localmente:

```text
PostgreSQL
Redis
Kafka
```

Posteriormente los servicios de la aplicación.

La aplicación debe poder arrancar con un proceso sencillo.

Documentar:

```bash
docker compose up
```

---

# 29. CI/CD

Crear GitHub Actions.

Pipeline inicial:

```text
checkout
↓
setup Java
↓
run tests
↓
build
```

Posteriormente:

```text
tests
↓
build Docker image
↓
push image
↓
deploy
```

No añadir despliegue complejo hasta que la aplicación sea estable.

---

# 30. OBSERVABILIDAD

Posteriormente implementar:

- structured logging
- correlation ID
- metrics
- distributed tracing

Utilizar:

```text
OpenTelemetry
Prometheus
Grafana
```

Debe ser posible seguir una operación:

```text
HTTP Request
↓
Payment Service
↓
Kafka
↓
Fraud Service
↓
Ledger Service
↓
Notification Service
```

---

# 31. AWS

Cuando la aplicación esté madura, estudiar despliegue en AWS.

Posibles servicios:

```text
EC2 / ECS
RDS PostgreSQL
ElastiCache Redis
MSK Kafka
CloudWatch
S3
```

No es obligatorio utilizar todos.

Elegir los servicios con una justificación clara.

---

# 32. KUBERNETES

Kubernetes es una fase avanzada y opcional.

NO implementarlo al principio.

Antes deben estar funcionando:

- Docker
- CI/CD
- microservicios
- health checks
- observabilidad
- configuración externa

---

# 33. DDD

Organizar el dominio utilizando conceptos de DDD cuando sean útiles.

Posibles bounded contexts:

```text
Identity
Accounts
Payments
Transfers
Ledger
Fraud
Notifications
Audit
```

No convertir DDD en una excusa para crear abstracciones innecesarias.

Priorizar código claro.

---

# 34. BASE DE DATOS

PostgreSQL será la base de datos principal.

Utilizar migraciones.

Preferentemente:

```text
Flyway
```

No depender de:

```text
spring.jpa.hibernate.ddl-auto=create
```

para producción.

Crear migraciones versionadas.

---

# 35. CALIDAD DEL CÓDIGO

Código:

- limpio
- mantenible
- legible
- modular
- testeable

Evitar:

- métodos gigantes
- clases gigantes
- lógica de negocio en controllers
- entidades expuestas directamente
- duplicación innecesaria
- abstracciones prematuras
- overengineering

Preferir soluciones sencillas antes que complejas.

---

# 36. DOCUMENTACIÓN

Crear un README profesional.

Debe explicar:

```text
¿Qué es FinPay?
Arquitectura
Stack
Cómo ejecutar
Arquitectura inicial
Evolución hacia microservicios
Kafka
Base de datos
Testing
Docker
CI/CD
Observabilidad
AWS
Decisiones arquitectónicas
```

Crear también una carpeta:

```text
/docs
```

con ADRs.

Ejemplos:

```text
ADR-001-modular-monolith.md
ADR-002-postgresql.md
ADR-003-kafka.md
ADR-004-idempotency.md
ADR-005-microservices.md
```

---

# 37. ROADMAP

Desarrollar siguiendo este orden.

## FASE 0

Preparación:

- estructura del repositorio
- README inicial
- arquitectura
- Docker básico
- configuración

## FASE 1

Backend inicial:

- Spring Boot
- PostgreSQL
- Flyway
- estructura modular
- health endpoint

## FASE 2

Usuarios:

- registro
- login
- JWT
- roles
- seguridad

## FASE 3

Accounts:

- creación
- consulta
- actualización
- bloqueo
- balance

## FASE 4

Ledger:

- double-entry
- ledger transactions
- ledger entries
- consistencia

## FASE 5

Transfers:

- transferencias
- validaciones
- transacciones
- concurrencia

## FASE 6

Payments:

- Payment Engine
- state machine
- idempotency

## FASE 7

Frontend:

- login
- dashboard
- accounts
- transfers
- payments
- transactions

## FASE 8

Testing:

- unit tests
- integration tests
- Testcontainers
- E2E

## FASE 9

Kafka:

- eventos
- producers
- consumers
- topics
- consumer groups
- retries
- DLQ
- idempotent consumers

## FASE 10

Fraud:

- reglas
- eventos
- procesamiento asíncrono

## FASE 11

Notifications:

- eventos
- procesamiento asíncrono
- historial

## FASE 12

Redis:

- cache
- idempotency
- rate limiting donde tenga sentido

## FASE 13

Microservicios:

Extraer progresivamente:

```text
Payment Service
Fraud Service
Ledger Service
Notification Service
```

No extraerlos todos simultáneamente.

## FASE 14

Docker completo.

## FASE 15

CI/CD.

## FASE 16

Observabilidad.

## FASE 17

AWS.

## FASE 18

Kubernetes opcional.

---

# 38. REGLAS PARA CODEX

Estas reglas son MUY IMPORTANTES.

### Regla 1

No implementes funcionalidades que todavía no corresponden a la fase actual.

### Regla 2

No hagas cambios masivos sin explicarlos.

### Regla 3

Antes de modificar arquitectura, explica el motivo.

### Regla 4

Después de cada cambio importante, ejecuta tests.

### Regla 5

Si encuentras un error, corrígelo antes de continuar.

### Regla 6

No ocultes errores utilizando:

```text
@SuppressWarnings
TODO
try/catch vacío
```

### Regla 7

No inventes dependencias innecesarias.

### Regla 8

Utiliza las versiones estables compatibles con Java 21 y Spring Boot.

### Regla 9

No almacenes secretos en el repositorio.

### Regla 10

No hagas overengineering.

Si una solución sencilla funciona, utilizarla.

### Regla 11

Cuando una decisión tenga varias alternativas, explica brevemente las opciones y selecciona una.

### Regla 12

Todas las operaciones financieras críticas deben estar cubiertas por tests.

### Regla 13

La aplicación debe poder ejecutarse localmente.

### Regla 14

Cada fase debe dejar el proyecto en un estado funcional.

### Regla 15

No crear microservicios únicamente para decir que el proyecto utiliza microservicios.

La arquitectura debe evolucionar porque existe una necesidad técnica.

---

# 39. FORMA DE TRABAJAR

Cuando te dé este prompt por primera vez:

NO empieces a crear toda la aplicación.

Primero:

1. Analiza el proyecto vacío.
2. Comprueba qué archivos existen.
3. Propón la estructura inicial.
4. Define la arquitectura de la FASE 0.
5. Implementa únicamente la FASE 0.
6. Ejecuta las comprobaciones necesarias.
7. Muéstrame el resultado.
8. Espera a que continuemos.

A partir de entonces trabajaremos fase por fase.

---

# 40. OBJETIVO FINAL

El resultado debe parecer un proyecto profesional que pueda enseñar en una entrevista técnica.

Debe demostrar que conozco:

```text
Java
Spring Boot
REST
SQL
PostgreSQL
Security
Transactions
Concurrency
Idempotency
Kafka
Event-driven architecture
Redis
Microservices
Testing
Docker
CI/CD
Cloud
Observability
```

Pero, sobre todo, debe demostrar que entiendo **por qué** utilizar cada tecnología y cuáles son sus ventajas y costes.

El objetivo NO es utilizar muchas tecnologías.

El objetivo es construir un sistema coherente y poder defender técnicamente cada decisión.

---

# INSTRUCCIÓN INICIAL

El repositorio está completamente vacío.

Comienza analizando el estado actual del repositorio y prepara únicamente la **FASE 0**.

No desarrolles todavía usuarios, pagos, Kafka, Redis ni microservicios.

Primero construye una base sólida sobre la que podamos desarrollar todo FinPay.
