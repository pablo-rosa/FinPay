# ADR-003: persistencia y propiedad de cuentas

## Estado

Aceptada para la Fase 3.

## Decisión

El módulo `accounts` usa Spring Data JPA y PostgreSQL. Flyway crea la tabla y Hibernate valida el esquema al arrancar (`ddl-auto=validate`). Cada cuenta pertenece a un usuario y todas las lecturas/actualizaciones de la API se filtran por el usuario autenticado.

La cuenta comienza con saldo cero y los cambios monetarios se realizan desde los postings del ledger. Los estados son `ACTIVE`, `BLOCKED` y `CLOSED`; el cierre es terminal y solo se permite con saldo cero. La columna `version` usa bloqueo optimista como detección adicional de escrituras concurrentes; los casos financieros críticos también bloquean filas con `PESSIMISTIC_WRITE` antes de validar el saldo.

## Consecuencias

- Los casos de uso deben conservar el orden de bloqueo de cuentas para reducir deadlocks y validar el saldo ya bloqueado.
- El monolito combina JDBC para usuarios/roles y JPA para cuentas sobre la misma base.
- El número de cuenta es generado por el sistema y único; no se usa como identificador de autorización.
