# API — README técnico

API REST de Gestión Cafetín: Spring Boot 4.1, Java 25, PostgreSQL 16 (Neon) y Flyway. La consumen
el panel web y la app Android. Contrato: [`docs/openapi.yaml`](../docs/openapi.yaml), congelado;
`ContratoOpenApiTest` falla si el código se aparta de él. Reglas de trabajo: [`CLAUDE.md`](CLAUDE.md).

## Arquitectura

Paquetes por módulo (`pe.edu.ucv.cafetin.<modulo>`) y, dentro, capas
`controller` → `service` → `repository`, con `dto` (records) y `modelo` (entidades JPA).

- Los controllers solo validan y delegan; la lógica va en los servicios. Nunca se exponen entidades.
- Errores: los servicios lanzan `ApiException` y `comun.error.ManejadorErrores` responde
  `{codigo, mensaje, detalles}`. Los clientes deciden por `codigo` (`CodigoError`).
- **La base hace parte del trabajo** con triggers que no se reimplementan en Java: total del pedido,
  descuento de stock al pasar un pago a `PAGADO` y alertas de stock. Detalle en el `CLAUDE.md` raíz.
- Seguridad sin sesión con JWT (HS256, 8 h). Roles `ESTUDIANTE`, `TRABAJADOR`, `DUENO`; las reglas por
  ruta están en `cuentas.seguridad.SecurityConfig`. Cada petición verifica que la cuenta siga activa.

## Módulos

| Módulo | Requisitos | Rutas | Piezas principales | Tests |
|---|---|---|---|---|
| `cuentas` | RF-01, RF-02, RF-03, RF-08 | `/api/auth/**`, `/api/trabajadores`, `/api/usuarios/me/**` | `SecurityConfig`, `TokenService`, `RecuperacionService`, `NotificacionPush`, `EnvioPushFcm`, `EnvioRecuperacionCorreo` | `AuthIntegracionTest`, `RecuperacionIntegracionTest`, `TrabajadorIntegracionTest`, `PushEstadoPedidoIntegracionTest`, `EnvioPushFcmTest`, `HealthCheckIntegracionTest` |
| `catalogo` | RF-04 | `GET /api/catalogo` | vista `v_catalogo_disponible` | `CatalogoIntegracionTest` |
| `pedidos` | RF-05, RF-06, RF-07, RF-09, RF-16 | `/api/pedidos/**` | `PedidoService`, `TransicionesPedido` (única clase que decide estados), `PasarelaPago` / `PasarelaSimulada` | `CrearPedidoIntegracionTest`, `PagoPedidoIntegracionTest`, `TransicionesPedidoTest` |
| `despacho` | RF-10 a RF-14 | `/api/despacho/**` | `DespachoService`; vista `v_pedidos_en_curso` | `DespachoIntegracionTest` |
| `inventario` | RF-15, RF-17, RF-18, RF-19 | `/api/productos/**`, `/api/inventario/**`, `/api/categorias/**`, `/api/proveedores/**`, `/api/alertas/**` | `ProductoService`, alertas desde `alerta_stock` y `v_alertas_reposicion` | `ProductoIntegracionTest`, `CategoriaProveedorIntegracionTest`, `RecargaAlertaIntegracionTest` |
| `reportes` | RF-20, RF-21, RF-22 | `/api/reportes/**` | vistas `v_ingresos_diarios`, `v_productos_mas_vendidos` | `ReporteIntegracionTest` |
| `comun` | — | — | errores, `PedidoEstadoCambiado` (evento), `Notificacion` | `ManejadorErroresTest`, `ContratoOpenApiTest` |

Ejemplos de flujo entre módulos:

- **Pagar** (`pedidos`): pago `PENDIENTE` → pasarela → `UPDATE` a `PAGADO`; el trigger descuenta el
  stock y, si el `CHECK` de stock falla, la API responde 409 `STOCK_INSUFICIENTE` y revierte todo.
  Un reintento tras un rechazo actualiza la misma fila de `pago`.
- **Avisar al estudiante** (`despacho` → `cuentas`): el despacho guarda la notificación y publica
  `PedidoEstadoCambiado`; `NotificacionPush` lo envía por FCM después del commit y en otro hilo.
- **Recuperar contraseña** (`cuentas`): se guarda el hash SHA-256 del token y el enlace sale por
  correo después del commit, en otro hilo: la respuesta tarda lo mismo exista o no la cuenta.

## Base de datos

- Migraciones en `src/main/resources/db/migration` (`V1__esquema.sql`, `V10…`); datos de prueba
  `db/datos-dev/V2__datos_prueba.sql`, solo en el perfil `dev`. Rangos por persona: Lizca V3–V9,
  Eliú V10–V19, Maria V20–V29 (`out-of-order` activado).
- Cada conexión fija `search_path=cafetin` con `options` (Neon ignora `currentSchema`).

## Configuración

| Variable | Para qué |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión **directa** a Neon (sin `-pooler`), `sslmode=require` |
| `JWT_SECRET` | Firma de los JWT, mínimo 32 bytes |
| `CORS_ORIGINS` | Orígenes del panel, separados por coma |
| `CAFETIN_RECUPERACION_ENLACEBASE` | Página del panel que recibe el token de recuperación |
| `SPRING_MAIL_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | Correo de recuperación (opcional; sin él, al log en `dev`) |
| `FIREBASE_CREDENTIALS` | Cuenta de servicio de Firebase, JSON o ruta (opcional; sin ella, el push va al log) |

Perfiles: `dev` (por defecto, con datos de prueba), `test` (Testcontainers) y `prod` (sin Swagger ni
datos de prueba). En local las variables se leen de `../.env` (ver [`.env.example`](../.env.example)).

## Comandos

```bash
./mvnw spring-boot:run   # http://localhost:8080 · Swagger en /swagger-ui.html
./mvnw verify            # tests con Testcontainers (PostgreSQL 16 real; requiere Docker)
                         # además mide cobertura (JaCoCo): falla si un *.service baja de 90 % líneas / 75 % ramas
                         # reporte en target/site/jacoco/index.html
```

Scripts en [`scripts/`](scripts/): `hash-contrasena.sh` (BCrypt para el primer dueño),
`datos_demo.py` (datos de la demo por la API), `pedido_demo.py` (hace de estudiante en la demo
mientras no exista la app) y `verificar-despliegue.sh` (revisa una API publicada).

## Despliegue

Imagen Docker multietapa ([`Dockerfile`](Dockerfile)) con caché AOT de Java 25 para que el arranque
en frío en el plan gratuito de Render sea más corto. Servicio descrito en
[`../render.yaml`](../render.yaml); pasos en [`docs/despliegue.md`](../docs/despliegue.md).
Health check para Render: `/actuator/health/liveness`.
