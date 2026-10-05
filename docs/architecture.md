# Arquitectura inicial

## Enfoque

FinPay comienza como un monolito modular. Un único proceso de aplicación contiene módulos organizados por capacidad de negocio. Esta forma permite mantener transacciones locales y una operación sencilla mientras se definen los límites del dominio.

## Módulos previstos

- `users`: identidad y perfil.
- `accounts`: cuentas y estado de cuenta.
- `payments`: ciclo de vida de pagos.
- `transfers`: transferencias entre cuentas.
- `ledger`: registro contable de movimientos.
- `fraud`: evaluación basada en reglas.
- `notifications`: notificaciones simuladas.
- `audit`: registro append-only de acciones relevantes.

Los módulos se crearán conforme avance el roadmap. `users` contiene el registro y acceso; `security` aplica autenticación bearer JWT y autorización por rol. Esta estructura es una dirección inicial, no una obligación de crear clases o abstracciones vacías desde el primer día.

## Persistencia e infraestructura

PostgreSQL es la base de datos principal. La instancia local se ejecuta en Docker Compose y conserva sus datos en un volumen nombrado. Las credenciales de desarrollo se configuran mediante variables de entorno y no se guardan como secretos de producción.

Flyway gestiona las tablas de identidad y revocación desde la Fase 2. Las contraseñas se guardan con hash BCrypt. Los JWT HS256 usan una clave externa al repositorio, expiran a los 15 minutos por defecto y tienen un `jti` que se revoca en PostgreSQL al cerrar sesión. Kafka, Redis y servicios distribuidos quedan fuera de esta base: se evaluarán cuando las necesidades del sistema lo justifiquen.

## Evolución

La extracción de un módulo a un servicio independiente requerirá una razón técnica observable, límites de dominio estables y una justificación de los costes de comunicación y operación. No se asume una migración automática a microservicios.
