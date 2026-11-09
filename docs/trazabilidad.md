# Matriz de trazabilidad: requisitos → pruebas → resultado

Estado al **09/11/2026**, con `main` más los PR pendientes de fusionar: el #15 (Flyway, Java 25,
despliegue, correo de recuperación, push, carga), el #14 de inventario y `feat/panel-contador-alertas`.
Con todo combinado, `./mvnw verify` pasa **189 tests** de la API y `npm test` pasa **31** del panel.

Responsable de mantenerla: José Daniel Lizca (Fase 6). Cada PR que agregue un RF o un test actualiza
su fila.

**Estados:** ✅ cubierto y probado · 🟡 parcial (falta una parte, indicada) · ❌ sin implementar

Los tests de la API están en `api/src/test/java/pe/edu/ucv/cafetin/`; los del panel, en `panel-web/src/`.

## Requisitos funcionales

| RF | Requisito | Pruebas | Resultado | Estado |
|---|---|---|---|---|
| RF-01 | Registro y login del estudiante | `cuentas/AuthIntegracionTest` (26: registro, BCrypt, duplicados → 409, login, contraseña errónea → 401, JWT vencido o falso → 401) · panel `App.test.tsx` (login) | ✅ API y panel | 🟡 falta la app Android |
| RF-02 | Trabajadores y dueño con roles | `cuentas/TrabajadorIntegracionTest` (8) · `AuthIntegracionTest` (reglas por rol) · `LoginDatosDevTest` (los 3 roles del seed) · panel `paginas/Trabajadores.test.tsx` (5) | ✅ API y panel | ✅ |
| RF-03 | Recuperar contraseña | `cuentas/RecuperacionIntegracionTest` (10: hash, expiración, un solo uso) · `EnvioRecuperacionCorreoTest` (3) · `EnvioRecuperacionSeleccionTest` (2) · `EnvioRecuperacionLogTest` (2) · panel `App.test.tsx` (`/recuperar`, `/restablecer`) · prueba manual con Mailpit (08/11) | ✅ | ✅ |
| RF-04 | Catálogo | `catalogo/CatalogoIntegracionTest` (4) · `inventario/CategoriaProveedorIntegracionTest` (lectura de categorías) | ✅ API | 🟡 falta la app Android |
| RF-05 | Carrito / crear pedido | `pedidos/CrearPedidoIntegracionTest` (13: total, precio del momento, stock, repetidos, sin ítems) | ✅ API | 🟡 falta la app Android |
| RF-06 | Pagar | `pedidos/PagoPedidoIntegracionTest` (13: aprobado, rechazado y reintento, doble pago, EFECTIVO → 400) | ✅ API | 🟡 falta la app Android |
| RF-07 | Recibo | `PagoPedidoIntegracionTest.pagoAprobadoDescuentaStockYEmiteRecibo` · `CrearPedidoIntegracionTest.elEstudianteVeSuPedido` | ✅ API | 🟡 falta la app Android |
| RF-08 | Notificar el estado | `cuentas/NotificacionIntegracionTest` (8: token FCM y bandeja) · `PushEstadoPedidoIntegracionTest` (6) · `EnvioPushFcmTest` (4, FCM simulado) · `EnvioPushSeleccionTest` (2) | ✅ API | 🟡 falta crear el proyecto de Firebase y que la app registre el token |
| RF-09 | Historial | `CrearPedidoIntegracionTest.historialMuestraSoloLosPropiosYLosMasRecientesPrimero`, `historialPagina` | ✅ API | 🟡 falta la app Android |
| RF-10 | Pedidos entrantes | `despacho/DespachoIntegracionTest` (lista solo pagados, filtro por estado) · panel `paginas/Despacho.test.tsx` (9) | ✅ | ✅ |
| RF-11 | Confirmar | `DespachoIntegracionTest.flujoCompletoHastaEntregarYNotificaAlEstudiante`, `confirmarUnPedidoSinPagarResponde409` | ✅ | ✅ |
| RF-12 | Marcar listo | `DespachoIntegracionTest` (flujo completo, saltarse un paso → 409) | ✅ | ✅ |
| RF-13 | Verificar recibo | `DespachoIntegracionTest.codigoIncorrectoResponde409YNoVerifica`, `verificarAntesDeEstarListoResponde409`, `codigoConFormatoInvalidoResponde400` | ✅ | ✅ |
| RF-14 | Entregar | `DespachoIntegracionTest.entregarSinReciboVerificadoResponde409` y el flujo completo | ✅ | ✅ |
| RF-15 | Alta de producto con stock | `inventario/ProductoIntegracionTest` (16) · `CategoriaProveedorIntegracionTest` (11) | ✅ API (PR #14) | 🟡 faltan las pantallas del dueño en el panel |
| RF-16 | Descuento automático | `basedatos/TriggersTest.pagoActualizadoAPagadoDescuentaStock`, `pagoInsertadoYaPagadoNoDescuentaStock` · `PagoPedidoIntegracionTest` · prueba de carga | ✅ | ✅ |
| RF-17 | Alerta de stock mínimo | `TriggersTest.alertaSoloAlCruzarElMinimoYAlAgotarse` · prueba de carga (una alerta por producto) · `inventario/RecargaAlertaIntegracionTest` (listar, orden, solo las del dueño, marcar atendida, 404, 403) · panel `componentes/ContadorAlertas.test.tsx` (5) | ✅ base, `/api/alertas` y contador del panel | 🟡 falta la pantalla de alertas del panel |
| RF-18 | Recarga de stock | `inventario/RecargaAlertaIntegracionTest` (suma y fecha, atiende las alertas del producto, no crea alertas, 400, 404, 403) | ✅ API | 🟡 falta la pantalla del dueño |
| RF-19 | Alta, edición y baja | `ProductoIntegracionTest` (editar, deshabilitar, habilitar) | ✅ API (PR #14) | 🟡 faltan las pantallas del dueño |
| RF-20 | Registrar venta | Cada pago `PAGADO` es la venta: `PagoPedidoIntegracionTest.montoEsElTotalDelPedido` · `reportes/ReporteIntegracionTest` (rango, orden, paginación, 30 días por defecto) | ✅ registro y `/api/reportes/ventas` | 🟡 falta la pantalla del dueño |
| RF-21 | Ingresos día/semana/mes | `ReporteIntegracionTest` (día, semana desde lunes, mes, solo `PAGADO`, rango inválido 400) | ✅ API | 🟡 falta la pantalla del dueño |
| RF-22 | Más vendidos | `ReporteIntegracionTest.masVendidosOrdenaPorUnidadesYSoloCuentaPagados`, límite 400 | ✅ API | 🟡 falta la pantalla del dueño |

## Requisitos no funcionales

| RNF | Requisito | Pruebas | Resultado | Estado |
|---|---|---|---|---|
| RNF-01 | Pocos pasos | Sin prueba automática. Pedido + pago son 2 llamadas a la API | — | 🟡 falta una prueba con usuarios (tareas cronometradas) |
| RNF-02 | Pedido + pago < 5 s | `pruebas/carga/pedido-y-pago.js` (k6) | p95 de 35 ms con carga sostenida (40 estudiantes) | 🟡 ✅ en local; falta repetirla contra Render |
| RNF-03 | Concurrencia en hora punta | `PagoPedidoIntegracionTest.pagoConcurrenteDeLaUltimaUnidadSoloPasaUno` · `ManejadorErroresIntegridadTest` · prueba de carga | 165 unidades → 165 ventas, ningún stock negativo, 0 % de errores | ✅ |
| RNF-04 | Cifrado / HTTPS | `AuthIntegracionTest.laContrasenaSeGuardaConBcrypt` · `RecuperacionIntegracionTest` (el token se guarda como hash) · sin datos de tarjeta (solo `referencia`) | ✅ en la API | 🟡 HTTPS lo dan Render y Vercel: confirmarlo al desplegar |
| RNF-05 | Inventario e ingresos solo para el dueño | `AuthIntegracionTest.estudianteEnReportesResponde403ConFormatoDeError`, `trabajadorNoEntraAInventarioNiAPedidosDeEstudiante` · `ProductoIntegracionTest.estudianteYTrabajadorNoAccedenAProductos` | ✅ | ✅ |
| RNF-06 | Disponibilidad | `cuentas/HealthCheckIntegracionTest` (3) | ✅ health check para Render | 🟡 falta desplegar y medir la disponibilidad real |
| RNF-07 | Android y web | Panel: 31 tests (`npm test`) | ✅ web | ❌ falta la app Android |
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

1. **App Android** (RF-01, RF-04 a RF-09 en el cliente, RNF-07): no hay proyecto todavía.
2. **Fase 4 del dueño**: recarga (RF-18), `/api/alertas` (RF-17), reportes (RF-20 a RF-22) y sus pantallas.
3. **Despliegue**: repetir la carga contra Render, confirmar HTTPS y medir la disponibilidad.
4. **RNF-01**: prueba con usuarios.
