# Guion de la demo

Demostración de punta a punta sobre la **versión desplegada** (Render + Vercel + Neon), sin depender
de ninguna laptop salvo para proyectar. Duración: unos 12 minutos. Se ensaya completo al menos una
vez antes de la presentación; después del ensayo se marca la versión con el tag `v1.0`.

Mientras no exista la app Android, `api/scripts/pedido_demo.py` hace de estudiante: crea y paga un
pedido de verdad contra la API publicada. Se explica así en la presentación.

## Preparación

**Una sola vez, después del despliegue** (`docs/despliegue.md`, pasos 1 a 6):

```bash
python3 api/scripts/datos_demo.py https://<servicio>.onrender.com --dueno <correo del dueño> --ventas 12
```

Crea el catálogo (con *Gelatina* agotada y *Galletas de avena* justo sobre el mínimo), dos
trabajadores, cinco estudiantes y ventas para los reportes.

**El día de la demo, 10 minutos antes:**

1. Abre `https://cafe-tin-web.vercel.app` para **despertar la API**: en el plan gratuito de Render
   tarda un par de minutos en arrancar si estaba dormida.
2. Revisa que todo esté en orden:
   ```bash
   api/scripts/verificar-despliegue.sh https://<servicio>.onrender.com
   ```
3. Deja abiertas tres ventanas:
   - **Navegador normal:** panel con la sesión del **dueño**.
   - **Ventana privada:** panel con la sesión de `trabajador.demo1@cafetin.pe`.
   - **Terminal** en la raíz del repo, para `pedido_demo.py`.
4. Ten las contraseñas a mano, sin proyectarlas.

## Guion

| # | Quién | Qué se hace | Qué se ve | RF |
|---|---|---|---|---|
| 1 | Dueño | **Productos** | Stock por producto; *Gelatina* **Agotado**; botón **Recargar** | RF-15, RF-19 |
| 2 | Dueño | **Reportes** | Ingresos del día, más vendidos y ventas de la carga de datos | RF-20, RF-21, RF-22 |
| 3 | Dueño | **Trabajadores → Nuevo trabajador** | Registra a alguien en vivo; aparece en la lista | RF-02 |
| 4 | Trabajador | **Despacho** | Tablero vacío o con pedidos anteriores | RF-10 |
| 5 | Estudiante | `python3 api/scripts/pedido_demo.py https://<servicio>.onrender.com --producto "Galletas de avena" --cantidad 2` | Imprime el pedido `PED-…` y el recibo `DR-…` | RF-05, RF-06, RF-07 |
| 6 | Trabajador | Esperar hasta 5 s | El pedido aparece solo en **Pendientes** | RF-10 |
| 7 | Dueño | Mirar el menú | El número junto a **Alertas** sube: las galletas cruzaron el mínimo | RF-16, RF-17 |
| 8 | Dueño | **Alertas** | *Stock bajo* con el teléfono del proveedor; **Marcar como atendida** | RF-17 |
| 9 | Trabajador | **Confirmar** → **Empezar a preparar** → **Marcar listo** | La tarjeta cambia de columna; el estudiante recibe avisos | RF-08, RF-11, RF-12 |
| 10 | Trabajador | Escribe un código equivocado y pulsa **Verificar** | La API lo rechaza: no se puede entregar | RF-13 |
| 11 | Trabajador | Escribe el código `DR-…` del paso 5 → **Verificar** → **Entregar** | **Recibo verificado**; el pedido sale del tablero | RF-13, RF-14 |
| 12 | Trabajador | Escribe `/reportes` en la barra de direcciones | Vuelve a Despacho: no tiene acceso | RNF-05 |
| 13 | Dueño | **Trabajadores → Desactivar** al del paso 4 | La sesión del trabajador se cierra en el siguiente refresco | RF-02 |
| 14 | Dueño | **Productos → Recargar stock** en Galletas | El stock sube y la alerta queda atendida | RF-18 |

**Para cerrar, sin hacerlo en vivo:** la prueba de carga (`pruebas/carga/README.md`) mostró que pedido
más pago tarda unos 70 ms con 40 estudiantes a la vez. También mostró que, con concurrencia, la base
nunca vende más de lo que hay (RNF-02, RNF-03). La matriz de trazabilidad (`docs/trazabilidad.md`)
une cada requisito con sus pruebas.

**Opcional, si el correo está configurado:** **¿Olvidaste tu contraseña?** con la cuenta del dueño
y abrir el enlace que llega (RF-03).

## Si algo falla

| Problema | Qué hacer |
|---|---|
| El panel no carga datos o tarda | La API está despertando: espera 1–2 minutos y recarga |
| `verificar-despliegue.sh` marca la base | Neon suspendido: vuelve a correrlo; si sigue, revisa el panel de Neon |
| El pedido no aparece en Despacho | Revisa que `pedido_demo.py` haya impreso el `PED-…`; espera 5 s |
| Render caído | Plan B en una laptop: misma imagen y mismos scripts contra `http://localhost:8080` (ver `pruebas/carga/README.md`, paso 1) |

## Después del ensayo

- Anota lo que falló o tardó y corrígelo antes de la presentación.
- Si el ensayo salió completo, marca la versión con `git tag -a v1.0 -m "Versión de la demo"` en
  `main` y súbela con `git push origin v1.0`.
