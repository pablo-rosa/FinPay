# ADR-006: estados de pago e idempotencia en PostgreSQL

## Estado

Aceptada para la Fase 6.

## Decisión

Los pagos usan una máquina de estados explícita: `CREATED → PENDING → AUTHORIZED → CAPTURED → COMPLETED`. Los pagos pendientes pueden rechazarse. Una captura que ya no puede ejecutarse por saldo o estado de cuenta pasa a `FAILED`. `REFUNDED` forma parte del enum para reflejar el ciclo definido, pero su transición y asiento se implementarán junto con reembolsos en una fase posterior.

La clave `Idempotency-Key` se guarda en PostgreSQL con usuario, hash SHA-256 de los datos normalizados y el identificador del pago. La restricción única por usuario y clave, junto con `INSERT ... ON CONFLICT DO NOTHING`, serializa reintentos concurrentes de creación. Una petición repetida con la misma huella devuelve el mismo recurso; una huella distinta produce conflicto. La idempotencia no cubre las transiciones de pago ni las transferencias.

La captura y los asientos del ledger comparten la transacción PostgreSQL. El estado del pago se bloquea al ejecutar cada acción y las cuentas se bloquean en orden UUID estable antes de validar o cambiar saldos.

## Consecuencias

- La autorización valida fondos/cuentas pero no reserva fondos; la captura vuelve a validar saldo porque otras operaciones pueden ocurrir entre ambas acciones. Puede terminar en `FAILED` aunque antes se autorizara.
- Las llamadas de autorización, rechazo, captura y finalización no pueden repetirse desde estados incompatibles.
- La idempotencia cubre únicamente la creación de pago; no existe clave idempotente para transferencias ni para repetir transiciones.
- No existe una operación normal de ingreso. La demo de portfolio sí registra una financiación ficticia mediante una contrapartida contable interna.
