# ADR-001: comenzar con un monolito modular

- Estado: aceptado
- Fecha: 2026-10-05

## Contexto

FinPay es un proyecto de portfolio que evolucionará en varias fases. Al comienzo aún no hay necesidades de escalado independiente ni equipos separados que justifiquen servicios distribuidos.

## Decisión

Desarrollar inicialmente un único backend organizado en módulos por capacidad de negocio. Usar PostgreSQL como persistencia compartida y mantener las dependencias entre módulos explícitas. Evaluar una extracción a microservicios más adelante, basándose en necesidades técnicas concretas.

## Consecuencias

- Las operaciones que lo requieran pueden usar transacciones de base de datos locales.
- El despliegue y el entorno de desarrollo empiezan con menos componentes.
- Los límites modulares deben cuidarse para evitar dependencias cruzadas y facilitar una extracción futura si se justifica.
- La aplicación no obtiene aislamiento de despliegue ni escalado independiente por módulo mientras siga siendo un monolito.
