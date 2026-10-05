# ADR-003: persistencia y propiedad de cuentas

## Estado

Aceptada para la Fase 3.

## Decisión

El módulo `accounts` usa Spring Data JPA y PostgreSQL. Flyway crea la tabla y Hibernate valida el esquema al arrancar (`ddl-auto=validate`). Cada cuenta pertenece a un usuario y todas las lecturas/actualizaciones de la API se filtran por el usuario autenticado.

El saldo comienza en cero y solo se expone para lectura hasta que el ledger proporcione movimientos contables. Los estados son `ACTIVE`, `BLOCKED` y `CLOSED`; el cierre es terminal y solo se permite con saldo cero. La columna `version` usa bloqueo optimista para evitar sobrescrituras silenciosas en actualizaciones simultáneas.

## Consecuencias

- Las actualizaciones concurrentes conflictivas deben reintentarse después de recargar la cuenta.
- El monolito combina JDBC para identidad existente y JPA para cuentas sobre la misma base.
- El número de cuenta es generado por el sistema y único; no se usa como identificador de autorización.
