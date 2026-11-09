# Gestión Cafetín

Pedidos y stock para las cafeterías de la UCV. El estudiante pide y paga desde el celular y recoge
sin hacer fila; el trabajador despacha desde un panel web; el dueño administra productos, stock e
ingresos. App y panel consumen **una sola API REST**.

Proyecto del curso Ingeniería de Software (UCV, 14/09/2026 – 18/12/2026).
Equipo: José Daniel Lizca · Eliú Alejandro Barrera Martínez · Maria Elizabeth Ruiz Díaz.

## Qué hay en el repositorio

| Carpeta | Qué es | Tecnología |
|---|---|---|
| [`api/`](api/README.md) | API REST: cuentas, pedidos, pagos, despacho, inventario y reportes | Spring Boot 4.1 · Java 25 · PostgreSQL 16 · Flyway |
| [`panel-web/`](panel-web/README.md) | Panel del trabajador (despacho) y del dueño | React 18 · TypeScript · Vite · TanStack Query |
| `app-android/` | App del estudiante (pendiente) | Kotlin · Jetpack Compose |
| [`docs/`](docs/) | Contrato de la API, manual, despliegue, trazabilidad, informe | — |
| [`pruebas/carga/`](pruebas/carga/README.md) | Prueba de carga de la hora punta | k6 |

## Arquitectura

```
App Android (APK) ──┐
                    ├──► API REST (Render, Docker) ──► PostgreSQL 16 (Neon)
Panel web (Vercel) ─┘        JWT · OpenAPI              triggers · vistas
```

| Nivel | Patrón | Cómo se aplica |
|---|---|---|
| Sistema | Cliente-servidor con una sola API REST | La app y el panel consumen la misma API; ningún cliente accede a la base |
| Despliegue | Tres capas en la nube | Presentación (Vercel / APK) → lógica (Render) → datos (Neon) |
| API | Capas dentro de paquetes por módulo | `cuentas`, `catalogo`, `pedidos`, `despacho`, `inventario`, `reportes`, `comun`; cada uno con `controller → service → repository` y DTOs (`record`) |
| | Contract-first | [`docs/openapi.yaml`](docs/openapi.yaml) es el contrato congelado; `ContratoOpenApiTest` verifica que la API lo cumpla |
| | Reglas en la base de datos | Triggers calculan el total del pedido, descuentan stock al pagar y generan alertas; Java no las reimplementa |
| | Errores centralizados | `@RestControllerAdvice` responde `{codigo, mensaje, detalles}` |
| | Interfaces para servicios externos | `PasarelaPago` (pago simulado) y `EnvioRecuperacion` (correo o log) |
| | Seguridad sin sesión | JWT con roles `ESTUDIANTE`, `TRABAJADOR` y `DUENO` |
| | Máquina de estados | Una sola clase decide las transiciones del pedido; cualquier otra responde 409 |
| Panel web | SPA por componentes | React; el estado del servidor lo gestiona TanStack Query |
| App Android | MVVM | View (Compose) → ViewModel → Repository (Retrofit) |
| Tiempo real | Polling + push | El panel consulta cada 5 s; el estudiante recibe notificaciones FCM |

## Herramientas

| Capa | Herramienta | Uso |
|---|---|---|
| API | Java 25 · Spring Boot 4.1 (Web MVC, Data JPA, Validation) | API REST, persistencia con Hibernate y validación |
| | Spring Security + OAuth2 Resource Server | Autenticación JWT (HS256, 8 h) y roles |
| | Spring Boot Mail | Recuperación de contraseña por correo (RF-03) |
| | Spring Boot Actuator | Health check para Render |
| | springdoc-openapi | Swagger UI |
| | Nimbus JOSE + `RestClient` | Push FCM por la API HTTP v1 |
| | Maven | Compilación y dependencias |
| Base de datos | PostgreSQL 16 en Neon | Una rama por desarrollador; `main` es producción |
| | Flyway | Migraciones versionadas |
| | PL/pgSQL | Triggers y vistas de reportes |
| Panel web | React 18 · TypeScript · Vite | Panel del trabajador y del dueño |
| | React Router · TanStack Query | Navegación, llamadas a la API y polling |
| | openapi-typescript | Tipos generados desde el contrato |
| App Android *(pendiente)* | Kotlin · Jetpack Compose · Retrofit + OkHttp · DataStore · FCM | App del estudiante |
| Pruebas | JUnit 5 · Spring Boot Test · Testcontainers | Integración contra PostgreSQL 16 real |
| | Vitest · Testing Library · jsdom | Tests del panel |
| | k6 | Prueba de carga de la hora punta (RNF-02, RNF-03) |
| Infraestructura | Docker (Temurin 25) · Render · Vercel | Despliegue de la API y del panel |
| | GitHub · GitHub Actions | Repositorio, PRs por tarea y CI |

## En producción

| Pieza | Dónde |
|---|---|
| Panel | https://cafe-tin-web.vercel.app (Vercel) |
| API | Render, servicio `cafetin-api` (`render.yaml`) |
| Base de datos | Neon, rama `main` |

Cómo publicar o volver a publicar: [`docs/despliegue.md`](docs/despliegue.md).

## Desarrollo local

Requisitos: JDK 25, Docker (los tests levantan PostgreSQL con Testcontainers) y Node 24 o superior.

```bash
cp .env.example .env              # y completar con la conexión de tu rama de Neon
cd api && ./mvnw spring-boot:run  # API en http://localhost:8080, Swagger en /swagger-ui.html
cd panel-web && npm ci && npm run dev
```

En el perfil `dev` la base se carga con datos de prueba; la contraseña de todos sus usuarios es
`Cafetin2026`.

## Pruebas

```bash
cd api && ./mvnw verify   # unitarias e integración contra PostgreSQL 16 real (Docker)
cd panel-web && npm test
```

Cada PR corre ambas en GitHub Actions. Qué prueba cubre cada requisito:
[`docs/trazabilidad.md`](docs/trazabilidad.md).

## Documentación

- [Manual de usuario por rol](docs/manual-usuario.md)
- [Guion de la demo](docs/guion-demo.md)
- [Despliegue](docs/despliegue.md)
- [Contrato de la API](docs/openapi.yaml) (OpenAPI 3; Swagger en local)
- [Plan de desarrollo](Plan%20de%20desarrollo.md) y reglas del proyecto en [`CLAUDE.md`](CLAUDE.md)
- [Informe académico](docs/Informe_Academico_Gestion_Cafetin.pdf)
