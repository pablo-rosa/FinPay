# ADR-008: estrategia de pruebas por capas

- Estado: aceptado
- Fecha: 2026-10-07

## Contexto

Las reglas financieras dependen de transacciones y bloqueos específicos de PostgreSQL, y el producto incluye además una interfaz web. Una prueba con mocks o una base distinta no demuestra las restricciones SQL, migraciones, carreras de concurrencia ni el recorrido del navegador.

## Decisión

- JUnit/AssertJ para pruebas unitarias de reglas de dominio.
- Spring Boot Test y MockMvc para integrar controladores, seguridad, servicios, persistencia y migraciones sobre PostgreSQL.
- Testcontainers para que cada clase de integración use una instancia temporal PostgreSQL 16, sin modificar la base local de desarrollo.
- Playwright para E2E del navegador contra backend y frontend activos.
- Las clases Testcontainers usan `disabledWithoutDocker=true`; sin un daemon accesible, JUnit las omite para permitir ejecutar las pruebas unitarias.

## Motivos

- Mantener rápidas y enfocadas las pruebas de reglas puras.
- Probar con el mismo motor relacional que usa la aplicación y no compartir las pruebas con datos personales del entorno local.
- Cubrir el flujo real de la demo desde la interfaz, incluyendo la sesión del navegador y el proxy API.
- Permitir que las pruebas unitarias sigan ejecutándose en entornos sin Docker.

## Consecuencias y límites

- Un `mvn test` puede terminar con exit code cero aunque se hayan omitido las integraciones. El equipo debe revisar los informes Surefire y el conteo `skipped`.
- Las pruebas Playwright requieren instalar Chromium y arrancar PostgreSQL, backend y frontend por separado.
- La última validación registrada tuvo éxito en las tres pruebas unitarias y en lint/build frontend, pero no ejecutó las integraciones por inaccesibilidad de Docker ni los escenarios E2E. La Fase 8 no debe considerarse completamente verificada hasta ejecutar ambas capas.
- PostgreSQL de desarrollo está fijado como imagen mayor 17 y el contenedor de test como mayor 16; una futura decisión puede alinear versiones si se requiere paridad exacta.
