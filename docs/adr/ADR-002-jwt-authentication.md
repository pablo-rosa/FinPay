# ADR-002: autenticar usuarios con JWT de corta duración

- Estado: aceptado
- Fecha: 2026-10-05

## Contexto

La Fase 2 necesita registro, login, autorización por roles y una API REST sin sesión de servidor. El monolito actual no depende de un proveedor de identidad externo.

## Decisión

Usar Spring Security como OAuth 2.0 Resource Server para verificar bearer JWT y emitir los tokens desde el módulo `users`. Firmarlos con HS256 usando `JWT_SECRET`, que se configura fuera de Git. Los tokens duran 15 minutos por defecto y contienen un `jti`, el identificador de usuario y roles. Guardar en PostgreSQL el `jti` revocado para que cerrar sesión invalide inmediatamente ese token.

Guardar contraseñas con `DelegatingPasswordEncoder`, que usa BCrypt al crearlas y conserva el identificador del algoritmo en el hash. El registro público asigna solo `USER`; las asignaciones `ADMIN` quedan fuera de ese flujo.

## Consecuencias

- La aplicación no necesita una tabla de sesiones para validar cada JWT; verifica firma y expiración localmente.
- La comprobación de revocación consulta PostgreSQL en cada solicitud autenticada, a cambio de que el logout tenga efecto inmediato.
- Una clave HS256 se comparte entre instancias del backend, por lo que su protección y futura rotación son importantes.
- Esta autenticación es adecuada para el monolito del portfolio; un proveedor OIDC externo podría sustituir la emisión propia si apareciera esa necesidad.
