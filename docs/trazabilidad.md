# Matriz de trazabilidad: requisitos → pruebas → resultado

Estado al **15/11/2026**: `main` más los dos PR de la app Android pendientes de fusionar, el #26
(cuentas) y el #27 (pedidos, que va sobre el #26). Resultados de ese día:

| Módulo | Comando | Tests |
|---|---|---|
| API | `cd api && ./mvnw verify` | **217** en verde |
| Panel web | `cd panel-web && npm test` | **69** en verde |
| App Android | `cd app-android && ./gradlew assembleDebug testDebugUnitTest` | **50** en verde (rama del #27) |

Responsable de mantenerla: José Daniel Lizca (Fase 6). Cada PR que agregue un RF o un test actualiza
su fila.

**Estados:** ✅ cubierto y probado · 🟡 parcial (falta una parte, indicada) · ❌ sin implementar

Los tests de la API están en `api/src/test/java/pe/edu/ucv/cafetin/`; los del panel, en `panel-web/src/`;
los de la app, en `app-android/app/src/test/java/pe/edu/ucv/cafetin/app/` (corren en la JVM, sin emulador).
Además, el 15/11 se probó la app en el emulador contra la API local: login, catálogo, carrito, pedido,
pago, recibo, avance del estado desde despacho y descuento de stock.

## Requisitos funcionales

| RF | Requisito | Pruebas | Resultado | Estado |
|---|---|---|---|---|
| RF-01 | Registro y login del estudiante | `cuentas/AuthIntegracionTest` (26: registro, BCrypt, duplicados → 409, login, contraseña errónea → 401, JWT vencido o falso → 401) · panel `App.test.tsx` (login) · app `ui/cuentas/CuentasViewModelsTest` (8), `ValidacionesTest` (4), `datos/CuentasRepositorioTest` (13: JWT, sesión guardada, 401 cierra la sesión, solo estudiantes) | ✅ API, panel y app | ✅ (app en el PR #26) |
| RF-02 | Trabajadores y dueño con roles | `cuentas/TrabajadorIntegracionTest` (8) · `AuthIntegracionTest` (reglas por rol) · `LoginDatosDevTest` (los 3 roles del seed) · panel `paginas/Trabajadores.test.tsx` (5) | ✅ API y panel | ✅ |
| RF-03 | Recuperar contraseña | `cuentas/RecuperacionIntegracionTest` (11: hash, expiración, un solo uso) · `EnvioRecuperacionCorreoTest` (3) · `EnvioRecuperacionSeleccionTest` (2) · `EnvioRecuperacionLogTest` (2) · panel `App.test.tsx` (`/recuperar`, `/restablecer`) · app `CuentasRepositorioTest` (202 sin cuerpo, error 400) · prueba manual con Mailpit (08/11) | ✅ | ✅ |
| RF-04 | Catálogo | `catalogo/CatalogoIntegracionTest` (4) · `inventario/CategoriaProveedorIntegracionTest` (lectura de categorías) · app `PedidosRepositorioTest.catalogoLeeLaDisponibilidad`, `PedidosViewModelsTest.catalogoCargaYFiltraPorCategoria` | ✅ API y app | ✅ (app en el PR #27) |
| RF-05 | Carrito / crear pedido | `pedidos/CrearPedidoIntegracionTest` (13: total, precio del momento, stock, repetidos, sin ítems) · app `ui/pedidos/CarritoTest` (9: stock, máximo de 20, céntimos, horas de recojo) · `PedidosViewModelsTest` (crear, carrito vacío, sin stock) | ✅ API y app | ✅ (app en el PR #27) |
| RF-06 | Pagar | `pedidos/PagoPedidoIntegracionTest` (14: aprobado, rechazado y reintento, doble pago, EFECTIVO → 400) · app `PedidosViewModelsTest.pagoRechazadoPermiteReintentarYLuegoMuestraElRecibo`, `pagoSinStockExplica…` · `PedidosRepositorioTest` (402) | ✅ API y app | ✅ (app en el PR #27) |
| RF-07 | Recibo | `PagoPedidoIntegracionTest.pagoAprobadoDescuentaStockYEmiteRecibo` · `CrearPedidoIntegracionTest.elEstudianteVeSuPedido` · app `PedidosRepositorioTest.pagarDevuelveElRecibo` | ✅ API y app | ✅ (app en el PR #27) |
| RF-08 | Notificar el estado | `cuentas/NotificacionIntegracionTest` (8: token FCM y bandeja) · `PushEstadoPedidoIntegracionTest` (7) · `EnvioPushFcmTest` (4, FCM simulado) · `EnvioPushSeleccionTest` (2) · app `PedidosViewModelsTest.abrirUnAvisoLoMarcaLeido`, `PedidosRepositorioTest.marcarAvisoLeidoAceptaEl204` | ✅ API · la app consulta el pedido abierto cada 5 s y la bandeja cada 10 s | 🟡 falta el push: crear el proyecto de Firebase y que la app registre el token y muestre la notificación |
| RF-09 | Historial | `CrearPedidoIntegracionTest.historialMuestraSoloLosPropiosYLosMasRecientesPrimero`, `historialPagina` · app `PedidosViewModelsTest.historialPideLaSiguientePaginaSoloSiHayMas`, `PedidosRepositorioTest.historialPideLaPaginaYElTamano` | ✅ API y app | ✅ (app en el PR #27) |
| RF-10 | Pedidos entrantes | `despacho/DespachoIntegracionTest` (12: lista solo pagados, filtro por estado) · panel `paginas/Despacho.test.tsx` (9) | ✅ | ✅ |
| RF-11 | Confirmar | `DespachoIntegracionTest.flujoCompletoHastaEntregarYNotificaAlEstudiante`, `confirmarUnPedidoSinPagarResponde409` | ✅ | ✅ |
| RF-12 | Marcar listo | `DespachoIntegracionTest` (flujo completo, saltarse un paso → 409) | ✅ | ✅ |
| RF-13 | Verificar recibo | `DespachoIntegracionTest.codigoIncorrectoResponde409YNoVerifica`, `verificarAntesDeEstarListoResponde409`, `codigoConFormatoInvalidoResponde400` | ✅ | ✅ |
| RF-14 | Entregar | `DespachoIntegracionTest.entregarSinReciboVerificadoResponde409` y el flujo completo | ✅ | ✅ |
| RF-15 | Alta de producto con stock | `inventario/ProductoIntegracionTest` (16) · `CategoriaProveedorIntegracionTest` (11) · panel `paginas/Productos.test.tsx` (registrar con stock, nombre repetido) · `CategoriasProveedores.test.tsx` (11) | ✅ API y panel | ✅ |
| RF-16 | Descuento automático | `basedatos/TriggersTest.pagoActualizadoAPagadoDescuentaStock`, `pagoInsertadoYaPagadoNoDescuentaStock` · `PagoPedidoIntegracionTest` · prueba de carga | ✅ | ✅ |
| RF-17 | Alerta de stock mínimo | `TriggersTest.alertaSoloAlCruzarElMinimoYAlAgotarse` · prueba de carga (una alerta por producto) · `inventario/RecargaAlertaIntegracionTest` (listar, orden, solo las del dueño, marcar atendida, 404, 403) · panel `componentes/ContadorAlertas.test.tsx` (5) · `paginas/Alertas.test.tsx` (6: pendientes, marcar atendida, refresco cada pocos segundos) | ✅ base, API y panel | ✅ |
| RF-18 | Recarga de stock | `inventario/RecargaAlertaIntegracionTest` (13: suma y fecha, atiende las alertas del producto, no crea alertas, 400, 404, 403) · panel `Productos.test.tsx` (recarga stock y muestra el nuevo) | ✅ API y panel | ✅ |
| RF-19 | Alta, edición y baja | `ProductoIntegracionTest` (editar, deshabilitar, habilitar) · panel `Productos.test.tsx` (editar sin el stock, deshabilitar y habilitar) | ✅ API y panel | ✅ |
| RF-20 | Registrar venta | Cada pago `PAGADO` es la venta: `PagoPedidoIntegracionTest.montoEsElTotalDelPedido` · `reportes/ReporteIntegracionTest` (12: rango, orden, paginación, 30 días por defecto) · panel `paginas/Reportes.test.tsx` (lista y pagina las ventas) | ✅ API y panel | ✅ |
| RF-21 | Ingresos día/semana/mes | `ReporteIntegracionTest` (día, semana desde lunes, mes, solo `PAGADO`, rango inválido 400) · panel `Reportes.test.tsx` (por día, semana o mes; rango inválido) | ✅ API y panel | ✅ |
| RF-22 | Más vendidos | `ReporteIntegracionTest.masVendidosOrdenaPorUnidadesYSoloCuentaPagados`, límite 400 · panel `Reportes.test.tsx` (ranking de más vendidos) | ✅ API y panel | ✅ |

## Requisitos no funcionales

| RNF | Requisito | Pruebas | Resultado | Estado |
|---|---|---|---|---|
| RNF-01 | Pocos pasos | Sin prueba automática. En la app, pedir y pagar son 4 toques desde el catálogo (agregar, carrito, continuar, pagar), con la hora de recojo ya elegida | — | 🟡 falta una prueba con usuarios (tareas cronometradas) |
| RNF-02 | Pedido + pago < 5 s | `pruebas/carga/pedido-y-pago.js` (k6) | p95 de 35 ms con carga sostenida (40 estudiantes) | 🟡 ✅ en local; falta repetirla contra Render |
| RNF-03 | Concurrencia en hora punta | `PagoPedidoIntegracionTest.pagoConcurrenteDeLaUltimaUnidadSoloPasaUno` · `ManejadorErroresIntegridadTest` · prueba de carga | 165 unidades → 165 ventas, ningún stock negativo, 0 % de errores | ✅ |
| RNF-04 | Cifrado / HTTPS | `AuthIntegracionTest.laContrasenaSeGuardaConBcrypt` · `RecuperacionIntegracionTest` (el token se guarda como hash) · sin datos de tarjeta (solo `referencia`) · HTTPS comprobado el 15/11: Render redirige `http://` → `https://` (301) y Vercel sirve por HTTPS · la app release exige HTTPS (solo `debug` admite HTTP a 10.0.2.2) | ✅ | ✅ |
| RNF-05 | Inventario e ingresos solo para el dueño | `AuthIntegracionTest.estudianteEnReportesResponde403ConFormatoDeError`, `trabajadorNoEntraAInventarioNiAPedidosDeEstudiante` · `ProductoIntegracionTest.estudianteYTrabajadorNoAccedenAProductos` | ✅ | ✅ |
| RNF-06 | Disponibilidad | `cuentas/HealthCheckIntegracionTest` (3) · `api/scripts/verificar-despliegue.sh` | ✅ desplegado en Render y Vercel; `/actuator/health` responde 200 (15/11) | 🟡 falta medir la disponibilidad durante un periodo |
| RNF-07 | Android y web | Panel: 69 tests (`npm test`) · app: 50 tests (`testDebugUnitTest`) y prueba manual en el emulador (15/11) | ✅ web y Android | ✅ (app en los PR #26 y #27) |
| RNF-08 | Escalable | API sin sesión (JWT), base gestionada (Neon) · prueba de carga | ≈ 14,7 pagos/s en una laptop | 🟡 sin prueba con varias instancias |

## Casos obligatorios de `CLAUDE.md`

| Caso | Test |
|---|---|
| Total del pedido | `TriggersTest.totalDelPedidoLoCalculaElTrigger`, `CrearPedidoIntegracionTest.pedidoDeDosProductosCalculaElTotalYQuedaPendienteSinPagar` |
| Descuento de stock al pagar | `PagoPedidoIntegracionTest.pagoAprobadoDescuentaStockYEmiteRecibo`, `TriggersTest.pagoActualizadoAPagadoDescuentaStock` |
| Pago concurrente de la última unidad (uno 409) | `PagoPedidoIntegracionTest.pagoConcurrenteDeLaUltimaUnidadSoloPasaUno` |
| Transición inválida (409) | `TransicionesPedidoTest.cualquierOtraCombinacionResponde409`, `DespachoIntegracionTest.saltarseUnPasoOConfirmarDosVecesResponde409` |
| Acceso denegado por rol (403) | `AuthIntegracionTest.estudianteEnReportesResponde403ConFormatoDeError` |
| Pago rechazado y reintento (una fila en `pago`) | `PagoPedidoIntegracionTest.pagoRechazadoConservaElPedidoYElReintentoUsaLaMismaFila` |
| Pedido ajeno (404) | `CrearPedidoIntegracionTest.pedidoAjenoOInexistenteResponde404`, `PagoPedidoIntegracionTest.pagarPedidoAjenoResponde404` |

Los siete existen y pasan.

## Lo que falta para cerrar la matriz

1. **Fusionar la app Android**: PR #26 (cuentas) y luego #27 (pedidos).
2. **Push con Firebase (RF-08)**: crear el proyecto, `google-services.json` (no se sube) y el receptor en
   la app. La API ya envía los avisos; mientras tanto la app los consulta sola.
3. **Despliegue**: repetir la prueba de carga contra Render con una rama de Neon de prueba (RNF-02) y
   medir la disponibilidad durante un periodo (RNF-06).
4. **RNF-01**: prueba con usuarios (tareas cronometradas).
5. **RNF-08**: prueba con varias instancias de la API.
