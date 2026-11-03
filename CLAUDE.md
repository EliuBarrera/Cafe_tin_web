# Gestión Cafetín — contexto para Claude Code

App de pedidos y stock para las cafeterías de la UCV. Los estudiantes piden y pagan desde una app
Android y recogen sin hacer fila; trabajadores y dueño usan un panel web. Ambos clientes consumen
**una sola API REST**. Proyecto del curso Ingeniería de Software (14/09/2026 – 18/12/2026).

Actores: **Estudiante** (pide, paga, recoge) · **Trabajador** (despacha) · **Dueño** (productos, stock, ingresos).

## Estructura del repo

```
api/           Spring Boot 4.1 · Java 21 · Maven · PostgreSQL 16 · Flyway
panel-web/     React 18 + TypeScript (Vite) · React Router · TanStack Query
app-android/   Kotlin + Jetpack Compose · MVVM · Retrofit · DataStore
docs/          openapi.yaml (contrato), trazabilidad.md, análisis y diagramas
.env.example   variables de entorno (conexión a Neon, JWT, CORS) sin valores reales
```

Infraestructura:

| Pieza | Dónde | Notas |
|---|---|---|
| Base de datos | **Neon** (PostgreSQL 16, AWS us-east-2) | Rama `main` = producción; una rama por persona para desarrollo (`dev-lizca`, `dev-eliu`, `dev-maria`) |
| API | **Render** (contenedor Docker, región Ohio) | Conexión **directa** a Neon (no la *pooled*), `sslmode=require` |
| Panel web | **Vercel** | Sitio estático de Vite; `VITE_API_URL` apunta a la API en Render |
| App Android | APK firmado | Distribución directa, sin Play Store |

Cada subcarpeta tiene su propio CLAUDE.md con reglas específicas.

## Comandos

```
cp .env.example .env                      # y completar con la conexión de TU rama de Neon
cd api && ./mvnw spring-boot:run          # API en :8080, Swagger en /swagger-ui.html
cd api && ./mvnw verify                   # tests (requiere Docker: Testcontainers)
cd panel-web && npm run dev | npm test
cd app-android && ./gradlew assembleDebug testDebugUnitTest
```

Una tarea está terminada solo cuando el comando de tests del módulo pasa.

## Base de datos

- Desarrollo contra tu rama de Neon; nunca contra la rama `main` de Neon (es producción).
  Los tests no usan Neon: usan Testcontainers (requieren Docker local).
- Zona horaria `America/Lima`: la fija V1 para la base (Neon trabaja en UTC). No la cambies en Java.
- Esquema `cafetin`. Migraciones en `api/src/main/resources/db/migration/` (`V1__esquema.sql`, `V3+`);
  datos de prueba en `api/src/main/resources/db/datos-dev/V2__datos_prueba.sql` (solo perfil `dev`).
  Contraseña de todos los usuarios del seed: `Cafetin2026`.
- `docs/01_…schema.sql` y `docs/02_…datos.sql` son el entregable académico original; la fuente
  de verdad para la API son las migraciones (las diferencias están en su cabecera).
- **Nunca edites una migración ya aplicada.** Cualquier cambio = nuevo archivo `V<n>__descripcion.sql`,
  y pregunta antes de crearlo.
- Tablas y columnas en `snake_case` y en español. Precios `NUMERIC(8,2)`. Contraseñas con BCrypt.
- 14 tablas. Herencia por subtipo: `usuario` → `estudiante` / `trabajador` / `dueno`;
  `notificacion` → `alerta_stock`. 1:1: `producto`–`inventario`, `pedido`–`pago`, `pago`–`recibo`.

### Lo que hace la BD (no lo reimplementes en Java)

| Trigger | Cuándo | Efecto |
|---|---|---|
| `trg_calcular_total_pedido` | INSERT/UPDATE/DELETE en `detalle_pedido` | Recalcula `pedido.total` |
| `trg_descontar_stock` | **UPDATE** de `pago` a `PAGADO` | Descuenta `inventario.cantidad_disponible` |
| `trg_alerta_stock_minimo` | UPDATE de `inventario` que **cruza** el mínimo o llega a 0 | Inserta `notificacion` + `alerta_stock` (`STOCK_BAJO` / `STOCK_AGOTADO`) para cada dueño. No cambia `producto.activo` |

Consecuencias:
- `pedido.total` y `detalle_pedido.subtotal` (columna GENERATED) son de **solo lectura** en JPA
  (`insertable = false, updatable = false`). Refresca el pedido después de guardar su detalle.
- El pago se crea `PENDIENTE` y luego se actualiza a `PAGADO`. Insertarlo ya `PAGADO` **no descuenta stock**.
- Tras confirmar un pago, no leas stock del contexto de persistencia: vuelve a consultar.
- `CHECK cantidad_disponible >= 0`: si falla al pagar → `DataIntegrityViolationException` → HTTP 409 "stock insuficiente".
- Vistas de solo lectura (`@Immutable`): `v_catalogo_disponible`, `v_pedidos_en_curso`,
  `v_ingresos_diarios`, `v_productos_mas_vendidos`, `v_alertas_reposicion`.
- `v_pedidos_en_curso` incluye pedidos sin pagar: en despacho filtra `estado_pago = 'PAGADO'`.

### Mapeo JPA

- `hibernate.default_schema=cafetin`, `ddl-auto: validate`.
- Enums nativos de PostgreSQL: `@Enumerated(EnumType.STRING)` + `@Column(columnDefinition = "<tipo>_enum")`
  (sin `columnDefinition`, `ddl-auto: validate` falla) y `stringtype=unspecified` en cada conexión.
- Cada conexión fija `currentSchema=cafetin` (application.yml): las funciones de los triggers nombran
  tablas sin esquema y fallan si el `search_path` es `public`. No lo quites.
- `Usuario` y `Notificacion`: `@Inheritance(strategy = JOINED)`; `Usuario` usa `@DiscriminatorColumn(name = "tipo_usuario")`.
- Entidades en `<modulo>.modelo` (p. ej. `pedidos.modelo.Pedido`). `Notificacion` está en `comun.modelo`.
- Columnas con DEFAULT de fecha (`fecha_registro`, `fecha_hora`, `fecha`…): `@Generated` + `insertable = false`.
  La hora la pone la base (Lima); no uses `LocalDateTime.now()` de Java para guardarlas.
- Relación LAZY hacia `Usuario` = proxy de la clase base: `instanceof Dueno` da `false`. Usa `getTipoUsuario()`.

## Arquitectura de la API

Paquetes por módulo y, dentro, capas `controller` → `service` → `repository` (+ `dto`):
`cuentas`, `catalogo`, `pedidos`, `despacho`, `inventario`, `reportes`, `comun`.
- La lógica va en `service`; los controllers solo validan y delegan. Nunca expongas entidades: usa DTOs.
- Errores con `@RestControllerAdvice` y formato `{codigo, mensaje, detalles}`.
- El contrato está en `docs/openapi.yaml`. No cambies rutas ni DTOs públicos sin avisar: app y panel dependen de ellos.

### Estados del pedido

`PENDIENTE` (pagado) → `CONFIRMADO` (RF-11, asigna trabajador) → `EN_PREPARACION` → `LISTO` (RF-12)
→ `ENTREGADO` (RF-14, exige recibo verificado, RF-13). `CANCELADO` solo desde `PENDIENTE` **sin pagar**
(un pedido con pago `PAGADO` no se cancela; no hay reembolsos en la v1).
Una sola clase decide las transiciones; cualquier otra → HTTP 409.
`PENDIENTE` cubre pedidos pagados y sin pagar: lo que los distingue es `pago.estado`, no `pedido.estado`.

Métodos de pago aceptados por la API: `YAPE`, `PLIN`, `TARJETA` (`EFECTIVO` existe en el enum de la BD
pero se rechaza con 400). Pasarela **simulada** detrás de la interfaz `PasarelaPago`; un rechazo responde
402 `PAGO_RECHAZADO` y el pedido se conserva. No se guardan datos de tarjeta, solo `referencia`.

### Pagos y códigos

- `pago` tiene `UNIQUE(id_pedido)`: un reintento tras `FALLIDO` **actualiza la misma fila**, nunca inserta otra.
- `pago.monto` = `pedido.total` refrescado de la BD; nunca un monto que mande el cliente.
- `codigo_pedido`: `PED-<secuencia>` sin relleno fijo (cabe en `VARCHAR(12)`).
- `recibo.codigo_verificacion`: formato `DR-<nº>-<4 dígitos aleatorios>` (informe, Tabla 16); reintentar si choca con el `UNIQUE`.

### Contrato y errores

- `docs/openapi.yaml` es el contrato congelado. `ContratoOpenApiTest` falla si el código se aparta de él.
  Si un cambio es a propósito y el equipo lo aprobó: `./mvnw test -Dtest=ContratoOpenApiTest -Dcontrato.actualizar=true`.
- DTOs como `record` en `<modulo>.dto`; controllers en `<modulo>.controller`. Endpoints sin implementar lanzan
  `ApiException.NoImplementado` (501).
- Errores: los servicios lanzan `ApiException` (o sus subclases `NoEncontrado`, `TransicionInvalida`, `ReglaNegocio`);
  `ManejadorErrores` arma `{codigo, mensaje, detalles}`. Los clientes deciden por `codigo` (`CodigoError`):
  `VALIDACION`, `CUERPO_INVALIDO`, `PARAMETRO_INVALIDO` (400) · `NO_AUTENTICADO` (401) · `PAGO_RECHAZADO` (402) ·
  `ACCESO_DENEGADO` (403) · `NO_ENCONTRADO` (404) · `CONFLICTO`, `STOCK_INSUFICIENTE`, `TRANSICION_INVALIDA`,
  `REGLA_NEGOCIO` (409) · `ERROR_INTERNO` (500) · `NO_IMPLEMENTADO` (501).
- La seguridad responde 401 y 403 con este mismo formato (`RespuestasSeguridad`).
- El despacho nunca devuelve `codigo_verificacion`: el trabajador lo pide al estudiante (RF-13).

### Seguridad

Spring Security sin sesión + JWT. Roles `ESTUDIANTE`, `TRABAJADOR`, `DUENO`.
- `/api/auth/**` público · `/api/pedidos/**` ESTUDIANTE · `/api/despacho/**` TRABAJADOR y DUENO
- `/api/inventario/**`, `/api/productos/**`, `/api/alertas/**`, `/api/reportes/**`, `/api/trabajadores/**` → solo DUENO (RNF-05)
- `GET /api/catalogo` y `GET /api/categorias` → autenticado · escritura en `/api/categorias/**` y `/api/proveedores/**` → DUENO
- `/api/usuarios/me/**` → autenticado · cualquier otra ruta → denegada
- Id del usuario autenticado: `UsuarioActual.id()` (`cuentas.seguridad`). En tests: `@WithMockUser(username = "4", roles = "ESTUDIANTE")`.
- `GET /api/pedidos/{id}`: solo el estudiante dueño del pedido; uno ajeno → 404.
- Token de recuperación de contraseña: se guarda su hash SHA-256, expira a los 30 min y es de un solo uso. La entrega va detrás de `EnvioRecuperacion`.
- Cada petición con JWT consulta si el usuario sigue activo: uno desactivado recibe 401 de inmediato.

## Pruebas

- Integración con **Testcontainers (PostgreSQL 16)**. No uses H2: no ejecuta triggers PL/pgSQL, enums ni estas vistas.
- Cada RF nuevo llega con al menos un test. Para reglas de negocio, escribe los tests primero.
- Casos que siempre deben existir: total del pedido, descuento de stock al pagar, pago concurrente
  de la última unidad (uno 409), transición inválida (409), acceso denegado por rol (403),
  pago rechazado y reintento (una sola fila en `pago`), pedido ajeno (404).

## Requisitos (referencia rápida)

- P1 Cuentas: RF-01 registro/login estudiante · RF-02 trabajadores y dueño con roles · RF-03 recuperar contraseña
- P2 Pedidos: RF-04 catálogo · RF-05 carrito · RF-06 pagar · RF-07 recibo · RF-08 notificar estado · RF-09 historial
- P3 Despacho: RF-10 pedidos entrantes · RF-11 confirmar · RF-12 listo · RF-13 verificar recibo · RF-14 entregado
- P4 Inventario: RF-15 alta con stock · RF-16 descuento automático · RF-17 alerta mínimo · RF-18 recarga · RF-19 alta/edición/baja
- P5 Ingresos: RF-20 registrar venta · RF-21 ingresos día/semana/mes · RF-22 más vendidos
- RNF: 01 pocos pasos · 02 pedido+pago < 5 s · 03 concurrencia en hora punta · 04 cifrado/HTTPS ·
  05 inventario e ingresos solo dueño · 06 disponibilidad · 07 Android + web · 08 escalable

## Decisiones del equipo

Anota aquí cada decisión cuando se cierre (fecha · decisión · motivo).
- 02/11/2026 · `usuario.fcm_token` va directo en V1 · V1 aún no se había aplicado; evita una V3 de parche.
- 02/11/2026 · Producto agotado: el trigger ya no pone `activo = FALSE`; el catálogo muestra `AGOTADO` por `estado_stock` y `activo` es solo la baja manual del dueño (RF-19) · `activo` tenía dos significados y al recargar no se sabía si reactivar. Pendiente: actualizar RN-03 del informe.
- 02/11/2026 · Alertas de stock solo al cruzar el mínimo o al llegar a 0 (cambio en `trg_alerta_stock_minimo`, V1) · antes se repetía una alerta en cada venta.
- 02/11/2026 · `Documentación/` pasa a `docs/`.
- 02/11/2026 · Base de datos en Neon, con una rama de Neon por persona para desarrollo; se elimina `docker-compose.yml` · sin PostgreSQL local y cada uno con sus datos.
- 02/11/2026 · Panel web en React + TypeScript (Vite), desplegado en Vercel · Vercel aloja el sitio estático; Thymeleaf no podría ir ahí.
- 02/11/2026 · API en Render como contenedor Docker · Vercel no ejecuta servidores Java.
- 02/11/2026 · Spring Boot 4.1 y Java 21 (no Boot 3) · Spring Initializr ya no ofrece Boot 3, que dejó de recibir parches gratuitos. Pendiente: actualizar el informe.
- 02/11/2026 · Pasarela de pago simulada detrás de la interfaz `PasarelaPago` · una real exige cuenta comercial y no aporta a lo que evalúa el curso.
- 02/11/2026 · Tiempo real: polling cada 5 s en el panel (despacho y alertas) y push FCM al estudiante; SSE solo si sobra tiempo · menor riesgo.
- 02/11/2026 · Sin pago en `EFECTIVO` en la v1: la API acepta YAPE, PLIN y TARJETA · en efectivo el stock no se reservaba y el despacho no veía esos pedidos. Pendiente: actualizar CU-02 del informe.
- 02/11/2026 · Solo se cancelan pedidos sin pagar; no hay reembolsos en la v1 · evita reponer stock y el flujo `REEMBOLSADO`.
- 02/11/2026 · JWT con `spring-boot-starter-security-oauth2-resource-server` (HS256, 8 h; `sub` = id, `rol` = tipo) · oficial de Spring, Boot gestiona la versión y evita un filtro propio.

## Qué no hacer

- No subas secretos (JWT, credenciales de Firebase, `.env`). Solo `.env.example` sin valores reales.
- No agregues dependencias nuevas sin preguntar.
- No modifiques triggers, vistas ni migraciones existentes por tu cuenta.
- No hagas commits grandes que mezclen módulos: una rama y un PR por tarea.
- Nunca hagas commit ni push a `main` (está protegida). Antes de editar, crea una rama desde `main`
  actualizado con el formato `<tipo>/<paquete>-<tarea>` (`feat/`, `fix/`, `docs/`, `test/`, `chore/`).
- No crees migraciones fuera del rango reservado del módulo (Lizca `V3`–`V9`, Eliú `V10`–`V19`, Maria `V20`–`V29`; ver "Reparto del equipo por módulos" en el plan).
- No edites `SecurityConfig`, `application.yml`, el paquete `comun` ni las entidades compartidas fuera de un PR que revise su dueño.
