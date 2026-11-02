# API — reglas del backend

Spring Boot 4.1 · Java 21 · Maven · PostgreSQL 16 (Neon) · Flyway. Se despliega en Render como
contenedor Docker. Lee primero el `CLAUDE.md` raíz (sección "Lo que hace la BD").

## Migraciones

- `src/main/resources/db/migration/` → todas las migraciones del esquema (`V1__esquema.sql`, `V3+`).
- `src/main/resources/db/datos-dev/` → `V2__datos_prueba.sql`, solo perfil `dev`:
  `spring.flyway.locations: classpath:db/migration,classpath:db/datos-dev`.
  Perfiles `test` y `prod` usan solo `classpath:db/migration`.
- Flyway: `spring.flyway.schemas: cafetin` (crea el esquema y fija el `search_path`).
- Nunca edites una migración aplicada. Usa el rango de tu módulo (ver "Qué no hacer" en el raíz)
  y pregunta antes de crearla.
- Contraseña de todos los usuarios del seed: `Cafetin2026`.

## Configuración

- Conexión por variables de entorno: `DB_URL`, `DB_USER`, `DB_PASSWORD` (ver `.env.example`).
  Nunca escribas credenciales en `application.yml`.
- Neon: usa la conexión **directa** (host sin `-pooler`) con `sslmode=require`. La *pooled*
  (PgBouncer en modo transacción) no sirve para Flyway.
- Propiedades de conexión (en `application.yml`, no en la URL): `stringtype=unspecified` (enums nativos)
  y `currentSchema=cafetin` (los triggers nombran tablas sin esquema; sin esto fallan desde la API).
- Paquete raíz `pe.edu.ucv.cafetin`. Perfil por defecto `dev`; los tests usan `@ActiveProfiles("test")`
  e `@Import(TestcontainersConfiguration.class)`.
- Pool de Hikari pequeño (`maximum-pool-size: 5`): el plan gratuito de Neon limita conexiones.
- Neon suspende la base sin uso: el primer request puede tardar ~1 s más. No lo "arregles" en código.
- `spring.jpa.hibernate.ddl-auto: validate`, `hibernate.default_schema: cafetin`.
- Zona horaria: la fija V1 en la base (`America/Lima`). No la cambies en Java.
- CORS: orígenes desde la variable `CORS_ORIGINS` (localhost del panel y el dominio de Vercel).

## Código

- Paquetes por módulo (`cuentas`, `catalogo`, `pedidos`, `despacho`, `inventario`, `reportes`, `comun`)
  y dentro `controller` / `service` / `repository` / `dto`.
- Tests de integración con Testcontainers (PostgreSQL 16, Docker local), nunca H2 ni Neon.
  Hecho = `./mvnw verify` en verde.
