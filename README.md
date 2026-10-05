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
4. Cuentas y ledger.
5. Transferencias y pagos.
6. Frontend, pruebas e integraciones introducidas cuando aporten valor.

El detalle de cada fase se irá incorporando cuando se implemente; esta base no incluye todavía código de aplicación.
