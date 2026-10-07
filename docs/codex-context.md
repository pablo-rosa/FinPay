# Contexto de FinPay para una nueva sesión de Codex

Este archivo permite retomar FinPay sin depender de la conversación anterior. Es una fotografía documental del repositorio; antes de cambiar código, verifica que los archivos actuales sigan coincidiendo con ella.

## Qué es el proyecto

FinPay es una simulación de banca/pagos para portfolio; no procesa dinero real. El backend es un monolito modular Spring Boot y PostgreSQL, con UI Next.js. Las transacciones financieras comparten una única base y el ledger es el registro contable interno. No migrar a microservicios ni añadir sistemas distribuidos por anticipación.

## Lectura recomendada antes de trabajar

1. `README.md`: estado general y fases.
2. `docs/architecture.md`: módulos y topología actual.
3. `docs/technical-overview.md`: flujo HTTP, persistencia, estados, APIs, tests y limitaciones.
4. ADR correspondiente al dominio que se vaya a tocar, en `docs/adr/`.
5. Código y migraciones del módulo afectado; la documentación no sustituye a la fuente.

El Prompt Maestro original fue un archivo adjunto local del autor y puede no estar disponible en otra máquina/checkout. Su roadmap es: Fase 0 preparación; 1 backend base; 2 identidad; 3 cuentas; 4 ledger; 5 transferencias; 6 pagos; 7 frontend; 8 testing; 9 Kafka; 10 fraude; 11 notificaciones; 12 Redis; 13 microservicios; 14 Docker completo; 15 CI/CD; 16 observabilidad; 17 AWS; 18 Kubernetes opcional. Si se necesita un requisito exacto del prompt que no está resumido aquí, pedir al usuario que proporcione el adjunto en la sesión actual.

## Estado de código y fase siguiente

- Fases 0–7: implementadas.
- Fase 8: hay 3 unit tests de estado de pago, 24 pruebas de integración JUnit/MockMvc sobre Testcontainers y 2 escenarios Playwright. La última ejecución conocida pasó las unitarias, omitió las 24 integraciones porque el proceso no pudo acceder al daemon Docker, y solo enumeró (no ejecutó) E2E. Lint y build del frontend pasaron. Antes de declarar Fase 8 validada, habilitar Docker para Testcontainers y ejecutar E2E con backend/frontend en marcha.
- Fases 9–18: pendientes. El siguiente paso de producto definido por el prompt es Kafka (Fase 9), una vez verificada Fase 8 y con alcance confirmado en el prompt.

No describas como implementadas las capacidades que solo tienen un paquete vacío. `fraud`, `notifications` y `audit` son placeholders. `/api/admin/**` está protegido por configuración, pero no tiene controladores administrativos.

## Reglas prácticas de trabajo

- En cada tarea, sigue el alcance del usuario y la fase indicada. No implementes fases posteriores salvo instrucción explícita.
- Para operaciones financieras, preservar atomicidad de ledger y negocio, doble partida, ownership, idempotencia existente y bloqueos en orden UUID; cubrirlas con pruebas según la solicitud/alcance.
- Usa Flyway para cambios de esquema; no cambies migraciones versionadas ya aplicadas. Hibernate solo valida (`ddl-auto=validate`).
- Mantén el secreto JWT y las credenciales de desarrollo en `.env` local, ignorado por Git. No leas, cites ni copies secretos de `.env` a respuestas o documentación. `.env.example` no debe incluir la cuenta admin privada.
- No borres el volumen de Docker ni datos del usuario sin una petición explícita. `docker compose down -v` destruye todos los datos locales.
- Backend: Java 21, Maven, Postgres. Frontend: Node 20.9+ y npm.
- Verifica Docker y el estado real de las pruebas antes de asegurar que Testcontainers corrió: JUnit está configurado para omitir integración si Docker no es detectable.
- Mantén los documentos sincronizados con el código; distingue claramente capacidades presentes, placeholders, planes y validaciones ejecutadas.

## Comandos de desarrollo

Desde la raíz, con `.env` configurado:

```powershell
docker compose up -d postgres
mvn test
mvn spring-boot:run
```

En otra terminal:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm install
npm run dev
```

Para E2E, con backend y frontend ejecutándose, desde `frontend`:

```powershell
npx playwright install chromium
npm run test:e2e
```

Puerto host de Postgres: `5433` por defecto. Backend: `8080`; Next.js: `3000`. La URL del backend para el proxy web se cambia con `FINPAY_API_URL`.

## Limitaciones que afectan decisiones

No hay depósito/retiro normal, reserva de fondos, reembolsos funcionales, idempotencia de transferencias, endpoint general de actividad, administración, Kafka, Redis, integración externa de pagos, empaquetado completo Docker, pipeline CI/CD, telemetría completa, AWS o Kubernetes. El rate limit de demo es local en memoria; sus registros no se purgan automáticamente. Revisa [technical-overview.md](technical-overview.md) para los detalles.
