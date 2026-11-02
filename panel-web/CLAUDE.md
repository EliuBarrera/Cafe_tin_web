# Panel web — reglas

Panel de la cafetería para TRABAJADOR (despacho) y DUENO (productos, inventario, alertas, reportes).
Responsable: Maria Elizabeth Ruiz. Lee primero el `CLAUDE.md` raíz.

Stack: React 18 + TypeScript (Vite), React Router, TanStack Query. Despliegue en **Vercel**.

- Solo consume la API REST; tipos generados desde `docs/openapi.yaml`. No inventes endpoints.
- Menú y rutas según el rol del JWT; la API sigue siendo quien valida (403).
- Despacho: refresco por polling cada 5 s.
- URL de la API en `VITE_API_URL` (`.env.local` en desarrollo; variable del proyecto en Vercel).
- Vercel: proyecto con *Root Directory* `panel-web`, framework Vite, salida `dist`.
  `vercel.json` con un rewrite de todas las rutas a `/index.html` para que React Router funcione
  al recargar la página.
- Hecho = `npm test` en verde.
