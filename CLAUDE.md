# Gestión Cafetín — contexto para Claude Code

App de pedidos y stock para las cafeterías de la UCV. Los estudiantes piden y pagan desde una app
Android y recogen sin hacer fila; trabajadores y dueño usan un panel web. Ambos clientes consumen
**una sola API REST**. Proyecto del curso Ingeniería de Software (14/09/2026 – 18/12/2026).

Actores: **Estudiante** (pide, paga, recoge) · **Trabajador** (despacha) · **Dueño** (productos, stock, ingresos).

## Estructura del repo

```
api/           Spring Boot 3 · Java 17+ · Maven · PostgreSQL 16 · Flyway
panel-web/     React 18 + TypeScript (Vite) · React Router · TanStack Query      [PROPUESTA — confirmar]
app-android/   Kotlin + Jetpack Compose · MVVM · Retrofit · DataStore
docs/          openapi.yaml (contrato), trazabilidad.md, análisis y diagramas
docker-compose.yml   PostgreSQL 16 para desarrollo (credenciales en .env)
```

Cada subcarpeta tiene su propio CLAUDE.md con reglas específicas.

## Comandos

```
docker compose up -d                      # base de datos de desarrollo
cd api && ./mvnw spring-boot:run          # API en :8080, Swagger en /swagger-ui.html
cd api && ./mvnw verify                   # tests (requiere Docker: Testcontainers)
cd panel-web && npm run dev | npm test
cd app-android && ./gradlew assembleDebug testDebugUnitTest
```

Una tarea está terminada solo cuando el comando de tests del módulo pasa.

## Base de datos

- Esquema `cafetin`. Migraciones en `api/src/main/resources/db/migration/`:
  `V1__esquema.sql` (modelo físico), `V2__datos_prueba.sql` (solo perfil `dev`).
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
| `trg_alerta_stock_minimo` | UPDATE de `inventario` que baja del mínimo | Inserta `notificacion` + `alerta_stock` para cada dueño |

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
- Enums nativos de PostgreSQL: `@Enumerated(EnumType.STRING)` y `stringtype=unspecified` en la URL JDBC.
- `Usuario` y `Notificacion`: `@Inheritance(strategy = JOINED)`; `Usuario` usa `@DiscriminatorColumn(name = "tipo_usuario")`.

## Arquitectura de la API

Paquetes por módulo y, dentro, capas `controller` → `service` → `repository` (+ `dto`):
`cuentas`, `catalogo`, `pedidos`, `despacho`, `inventario`, `reportes`, `comun`.
- La lógica va en `service`; los controllers solo validan y delegan. Nunca expongas entidades: usa DTOs.
- Errores con `@RestControllerAdvice` y formato `{codigo, mensaje, detalles}`.
- El contrato está en `docs/openapi.yaml`. No cambies rutas ni DTOs públicos sin avisar: app y panel dependen de ellos.

### Estados del pedido

`PENDIENTE` (pagado) → `CONFIRMADO` (RF-11, asigna trabajador) → `EN_PREPARACION` → `LISTO` (RF-12)
→ `ENTREGADO` (RF-14, exige recibo verificado, RF-13). `CANCELADO` solo desde `PENDIENTE`.
Una sola clase decide las transiciones; cualquier otra → HTTP 409.

Métodos de pago: `YAPE`, `PLIN`, `TARJETA`, `EFECTIVO`. Pasarela **simulada** detrás de la interfaz
`PasarelaPago` [PROPUESTA — confirmar]. No se guardan datos de tarjeta, solo `referencia`.

### Seguridad

Spring Security sin sesión + JWT. Roles `ESTUDIANTE`, `TRABAJADOR`, `DUENO`.
- `/api/auth/**` público · `/api/pedidos/**` ESTUDIANTE · `/api/despacho/**` TRABAJADOR y DUENO
- `/api/inventario/**`, `/api/productos/**` (escritura), `/api/alertas/**`, `/api/reportes/**`, `/api/trabajadores/**` → solo DUENO (RNF-05)
- Token de recuperación de contraseña: guardar hash, con expiración.

## Pruebas

- Integración con **Testcontainers (PostgreSQL 16)**. No uses H2: no ejecuta triggers PL/pgSQL, enums ni estas vistas.
- Cada RF nuevo llega con al menos un test. Para reglas de negocio, escribe los tests primero.
- Casos que siempre deben existir: total del pedido, descuento de stock al pagar, pago concurrente
  de la última unidad (uno 409), transición inválida (409), acceso denegado por rol (403).

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
- [pendiente] Panel web: React + TypeScript vs Thymeleaf
- [pendiente] Pasarela simulada
- [pendiente] `usuario.fcm_token` (migración V3)
- [pendiente] Producto agotado: ¿`activo = FALSE` por trigger o filtro por `estado_stock`?
- [pendiente] Tiempo real: polling cada 5 s en el panel; push FCM al estudiante

## Qué no hacer

- No subas secretos (JWT, credenciales de Firebase, `.env`). Solo `.env.example` sin valores reales.
- No agregues dependencias nuevas sin preguntar.
- No modifiques triggers, vistas ni migraciones existentes por tu cuenta.
- No hagas commits grandes que mezclen módulos: una rama y un PR por tarea.
