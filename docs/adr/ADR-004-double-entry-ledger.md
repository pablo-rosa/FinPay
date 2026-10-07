# ADR-004: ledger de doble entrada

## Estado

Aceptada para la Fase 4.

## Decisión

El ledger persiste transacciones y entradas separadas. Cada posting exige al menos un débito y un crédito, y la suma de ambos lados debe coincidir exactamente. Todos los postings de una transacción usan la misma moneda. El ledger bloquea las cuentas por UUID ordenado, valida estado y fondos, registra las entradas y actualiza los saldos dentro de una única transacción PostgreSQL.

Las filas de transacciones y entradas son append-only, con restricciones y triggers de base de datos que rechazan cambios o borrados. El ledger sigue siendo un servicio interno de aplicación: transferencias y capturas de pagos lo invocan y exponen sus propias APIs. La demo también registra su financiación ficticia usando una contrapartida interna `DEMO_CAPITAL`.

## Consecuencias

- Una transacción concurrente sobre las mismas cuentas espera el bloqueo y valida el saldo actualizado antes de postear.
- Los importes usan `NUMERIC(19,4)` y no aceptan redondeos implícitos.
- Una referencia es única y sirve para impedir duplicados internos; la idempotencia de API para creación de pagos se resuelve separadamente con la tabla de claves de pago.
- La base de datos impide mutaciones y valida restricciones por fila. El balance global de débitos/créditos lo valida `LedgerService`; no hay constraint diferido que por sí mismo garantice balance por transacción.
- Las fixtures de integración inicializan un saldo de cuenta para probar débitos; en producción todo cambio posterior al saldo se aplica desde postings.
