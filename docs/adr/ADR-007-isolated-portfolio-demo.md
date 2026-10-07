# ADR-007: sesiones aisladas para la demo de portfolio

- Estado: aceptado
- Fecha: 2026-10-07

## Contexto

FinPay necesita una forma de permitir que una persona explore la interfaz sin registrar sus credenciales propias. La aplicación no debe ofrecer una ruta de ingreso general ni permitir que visitantes compartan una misma cuenta con fondos ficticios.

## Decisión

Cuando `FINPAY_DEMO_ENABLED=true`, cada llamada permitida a `POST /api/demo/session` crea un usuario `USER` con contraseña aleatoria no entregada al cliente, dos cuentas EUR nuevas y una financiación ficticia de la cuenta principal. La sesión usa el JWT normal de FinPay. El ledger registra el crédito frente a una contrapartida interna `DEMO_CAPITAL`; la API de creación de cuentas normales mantiene saldo cero.

Se aplica un límite por IP en memoria: 10 sesiones por hora por defecto. Los registros de demo no se borran automáticamente. La UI almacena un indicador de demo en `sessionStorage` y muestra el aviso de fondos simulados.

## Motivos

- La sesión puede explorar los endpoints de negocio existentes con las mismas reglas de cuentas, transferencias y pagos.
- Cada visitante tiene propietario y cuentas separados, lo que evita cruces de datos en el historial.
- Los fondos quedan representados en el ledger sin exponer un endpoint de depósito.
- La demo no concede privilegios administrativos ni revela una contraseña reutilizable.

## Consecuencias y límites

- La tabla de usuarios/cuentas/ledger crece hasta que se reinicie o limpie manualmente la base.
- El rate limit no se comparte entre instancias y se reinicia al reiniciar el proceso; no es un control distribuido de producción.
- Los fondos son simulados y la cuenta contraparte no equivale a una cuenta bancaria externa.
- Fuera del entorno que la habilita, el endpoint no está registrado.
