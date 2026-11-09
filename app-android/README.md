# App Android — README técnico

App del estudiante de Gestión Cafetín: Kotlin, Jetpack Compose (Material 3), MVVM, Navigation
Compose, Retrofit + OkHttp y DataStore. Reglas de trabajo: [`CLAUDE.md`](CLAUDE.md).

**Estado (09/11/2026):** cuentas listas (login, registro, recuperar contraseña, sesión guardada y
permiso de notificaciones). Falta: catálogo, carrito, pago, recibo e historial (RF-04 a RF-09) y el
push con Firebase (RF-08), que necesita el proyecto de Firebase y su `google-services.json`.

## Estructura

```
app/src/main/java/pe/edu/ucv/cafetin/app/
  CafetinApp.kt          dependencias armadas a mano (almacén, API, repositorio)
  MainActivity.kt        tema y navegación
  datos/                 DTOs del contrato, ApiCafetin (Retrofit), ClienteApi (OkHttp + JWT + errores),
                         AlmacenSesion (DataStore) y CuentasRepositorio
  ui/                    Tema, Navegacion (revisa la sesión al abrir)
  ui/cuentas/            pantallas de login, registro y recuperación, sus ViewModels y Validaciones
  ui/inicio/             inicio del estudiante (aquí va el catálogo)
app/src/debug/           permite HTTP solo hacia 10.0.2.2 y localhost (API local)
```

- Los errores de la API llegan como `ErrorApi(estado, codigo, mensaje, detalles)`: se decide por
  `codigo`, igual que en el panel. Sin conexión, el código es `SIN_CONEXION`.
- El JWT vive en DataStore y en memoria; un interceptor lo añade a cada petición salvo a `/api/auth`.
  Al abrir la app, `GET /api/usuarios/me` confirma la sesión; un 401 la borra.
- Solo entran estudiantes: un trabajador o el dueño reciben "usa el panel web".

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
