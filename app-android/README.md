# App Android — README técnico

App del estudiante de Gestión Cafetín: Kotlin, Jetpack Compose (Material 3), MVVM, Navigation
Compose, Retrofit + OkHttp y DataStore. Reglas de trabajo: [`CLAUDE.md`](CLAUDE.md).

**Estado (15/11/2026):** cuentas (login, registro, recuperar contraseña, sesión guardada y permiso de
notificaciones) y pedidos del estudiante: catálogo con filtro por categoría (RF-04), carrito con hora de
recojo (RF-05), pago simulado con reintento (RF-06), recibo con su código (RF-07), estado que se
actualiza solo y pestaña de avisos (RF-08) e historial paginado (RF-09). Falta el push con Firebase
(RF-08), que necesita el proyecto de Firebase y su `google-services.json`; mientras tanto, el detalle
de un pedido abierto se consulta cada 5 s y los avisos cada 10 s.

## Estructura

```
app/src/main/java/pe/edu/ucv/cafetin/app/
  CafetinApp.kt          dependencias armadas a mano (almacén, API, repositorio)
  MainActivity.kt        tema y navegación
  datos/                 DTOs del contrato, ApiCafetin (Retrofit), ClienteApi (OkHttp + JWT + errores),
                         AlmacenSesion (DataStore), CuentasRepositorio y PedidosRepositorio
  ui/                    Tema, Navegacion (revisa la sesión al abrir)
  ui/cuentas/            pantallas de login, registro y recuperación, sus ViewModels y Validaciones
  ui/inicio/             permiso de notificaciones
  ui/pedidos/            Carrito (en memoria), Formatos (soles, horas de recojo, estados), ViewModels y
                         pantallas: catálogo, carrito, pago, detalle/recibo, historial y avisos
app/src/debug/           permite HTTP solo hacia 10.0.2.2 y localhost (API local)
```

- Los errores de la API llegan como `ErrorApi(estado, codigo, mensaje, detalles)`: se decide por
  `codigo`, igual que en el panel. Sin conexión, el código es `SIN_CONEXION`.
- El JWT vive en DataStore y en memoria; un interceptor lo añade a cada petición salvo a `/api/auth`.
  Al abrir la app, `GET /api/usuarios/me` confirma la sesión; un 401 la borra.
- Solo entran estudiantes: un trabajador o el dueño reciben "usa el panel web".
- Flujo de compra: catálogo → carrito → `POST /api/pedidos` → pago → recibo. El pedido se crea antes de
  pagar: si el estudiante sale sin pagar, lo encuentra en Pedidos con "Pagar" y "Cancelar". El total que
  vale es el de la base; el del carrito es solo referencia (se calcula en céntimos).
- Navegación inferior: Catálogo, Carrito (con el número de unidades), Pedidos y Avisos.

## Requisitos y comandos

JDK **21** (Gradle y el plugin de Android aún no usan el 25 de la API) y el SDK de Android con la
plataforma 37 y build-tools 37.0.0. `local.properties` (no se sube) indica dónde está el SDK:
`sdk.dir=/home/<usuario>/Android/Sdk`.

```bash
cd app-android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug testDebugUnitTest
```

El APK de debug queda en `app/build/outputs/apk/debug/app-debug.apk` y apunta a
`http://10.0.2.2:8080` (la API local vista desde el emulador). El release usa `cafetin.apiUrl` de
`gradle.properties` (Render) y exige HTTPS; se puede cambiar con `-Pcafetin.apiUrl=https://…`.

Los tests (`testDebugUnitTest`) corren en la JVM sin emulador: validaciones, ViewModels con un
repositorio falso y el cliente HTTP contra una API simulada con MockWebServer.
