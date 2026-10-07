# ADR-005: transferencias atómicas sobre el ledger

## Estado

Aceptada para la Fase 5.

## Decisión

La API de transferencias crea una operación y dos entradas del ledger dentro de una única transacción PostgreSQL. Solo se permite debitar una cuenta activa propiedad del usuario autenticado y acreditar otra cuenta activa de la misma moneda. Se rechazan transferencias a la misma cuenta, importes no positivos, saldo insuficiente y transferencias desde cuentas ajenas.

Antes de validar el saldo, el servicio bloquea las dos filas de cuenta en orden UUID estable. Esto serializa las transferencias concurrentes sobre una cuenta y evita el doble gasto. La transferencia referencia su transacción de ledger y no se confirma si falla cualquiera de las escrituras.

## Consecuencias

- El historial registra transferencias completadas; los fallos no dejan ni transferencia ni asientos parciales.
- La clave de idempotencia de cliente se pospone a la Fase 6, como indica el roadmap.
- La prueba de integración prepara fondos iniciales como fixture; no se añade un endpoint de depósito fuera de esta fase.
