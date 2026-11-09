# Panel web — README técnico

Panel de Gestión Cafetín para el **trabajador** (despacho) y el **dueño** (productos, stock,
alertas, reportes, trabajadores). React 18 + TypeScript con Vite, React Router 7 y TanStack Query.
Publicado en https://cafe-tin-web.vercel.app. Reglas de trabajo: [`CLAUDE.md`](CLAUDE.md).

## Estructura

```
src/
  App.tsx              rutas públicas (login, recuperar, restablecer) y protegidas
  navegacion.tsx       secciones del menú: ruta, título, roles y página (una sola lista)
  api/cliente.ts       api(): añade el JWT y convierte los errores en ErrorApi {estado, codigo}
  api/tipos.ts         tipos de la app, derivados de esquema.d.ts (generado del contrato)
  sesion/              token en el navegador, usuario actual y RutaProtegida
  componentes/         Shell (barra y menú), ContadorAlertas, formularios, mensajes de error
  paginas/             una por sección, cada una con su .test.tsx
```

## Pantallas

| Sección | Roles | Requisitos | API |
|---|---|---|---|
| Login, Recuperar, Restablecer | público | RF-01, RF-03 | `/api/auth/**` |
| Despacho | trabajador, dueño | RF-10 a RF-14 | `/api/despacho/**` (refresco cada 5 s) |
| Productos | dueño | RF-15, RF-18, RF-19 | `/api/productos/**`, `/api/inventario/**` |
| Categorías, Proveedores | dueño | RF-15 | `/api/categorias/**`, `/api/proveedores/**` |
| Alertas (+ contador del menú) | dueño | RF-17 | `/api/alertas/**` (contador cada 5 s) |
| Reportes | dueño | RF-20 a RF-22 | `/api/reportes/**` |
| Trabajadores | dueño | RF-02 | `/api/trabajadores/**` |

El menú y las rutas salen de `navegacion.tsx` según el rol del usuario; la API es la que realmente
valida (403). Si la API rechaza el token (vencido o cuenta desactivada), el panel cierra la sesión.

## Convenciones

- Todas las peticiones pasan por `api()`; los errores se muestran con `MensajeError` y se deciden por
  `codigo`, nunca por el texto.
- Los tipos salen del contrato: `npm run api:tipos` regenera `src/api/esquema.d.ts` desde
  `docs/openapi.yaml`. No inventes endpoints.
- Polling cada 5 s (decisión del 02/11/2026) en despacho y en el contador de alertas; con la pestaña
  oculta no consulta.
- Tests con Vitest y Testing Library: renderizan `App` completa con una API falsa (`fetch`
  sustituido) y buscan por rol y texto accesible, como lo usaría una persona.

## Comandos

```bash
npm ci
npm run dev      # http://localhost:5173, contra VITE_API_URL (por defecto http://localhost:8080)
npm test
npm run build    # comprueba tipos y genera dist/
```

`VITE_API_URL` va en `.env.local` en desarrollo y en las variables del proyecto de Vercel en
producción. Vite la lee **al compilar**: después de cambiarla en Vercel hay que redesplegar.
`vercel.json` reenvía todas las rutas a `index.html` para que React Router funcione al recargar.
