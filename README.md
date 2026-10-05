# FinPay

FinPay es una simulación de una plataforma bancaria y de pagos, creada como proyecto de portfolio para practicar decisiones de ingeniería de software. No procesa dinero real.

## Estado

El proyecto comienza como un monolito modular. La Fase 0 preparó el entorno local. La Fase 1 añade la aplicación Spring Boot, conectividad PostgreSQL, migraciones Flyway y un endpoint de salud; aún no hay funcionalidades de negocio.

## Arquitectura inicial

La aplicación se organizará por módulos de negocio dentro de un único despliegue: `users`, `accounts`, `payments`, `transfers`, `ledger`, `fraud`, `notifications` y `audit`. Los módulos compartirán PostgreSQL al inicio y mantendrán límites explícitos. No se introducirán microservicios ni mensajería hasta que exista una necesidad concreta.

PostgreSQL es la base de datos principal. Docker Compose proporciona una instancia local con volumen persistente. Flyway crea el esquema `finpay` al iniciar la aplicación. Las credenciales predeterminadas de Compose son exclusivamente para desarrollo local y no deben reutilizarse fuera de ese entorno.

Consulta [la descripción de arquitectura](docs/architecture.md) y el [ADR-001](docs/adr/ADR-001-modular-monolith.md) para conocer las decisiones iniciales.

## Requisitos

- Docker Desktop con Docker Compose v2.
- Java 21 y Maven para las fases que incorporen el backend.

## Entorno local

Opcionalmente, copia `.env.example` a `.env` y ajusta los valores locales. `.env` está excluido de Git.

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

El endpoint de salud está disponible en `http://localhost:8080/actuator/health`. Las credenciales y la URL JDBC pueden configurarse mediante `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

## Roadmap

1. Preparación del repositorio y del entorno local.
2. Backend inicial con Spring Boot, PostgreSQL, Flyway y health endpoint (implementada).
3. Identidad y usuarios.
4. Cuentas y ledger.
5. Transferencias y pagos.
6. Frontend, pruebas e integraciones introducidas cuando aporten valor.

El detalle de cada fase se irá incorporando cuando se implemente; esta base no incluye todavía código de aplicación.
