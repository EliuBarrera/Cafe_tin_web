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
