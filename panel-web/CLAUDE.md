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
- React 18 por decisión del equipo: por eso `react-router` va en la v7 (la v8 exige React 19).
- Tipos: `npm run api:tipos` regenera `src/api/esquema.d.ts`; usa `src/api/tipos.ts`.
- Peticiones siempre con `api()` de `src/api/cliente.ts`: añade el JWT y convierte los errores en `ErrorApi`.
- Pantalla nueva: en `src/navegacion.tsx` cambia `pagina` de su sección por el componente real;
  el menú y la protección por rol salen de esa misma lista.
- Hecho = `npm test` en verde.
