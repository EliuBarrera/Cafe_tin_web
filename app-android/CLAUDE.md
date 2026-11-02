# App Android — reglas

App del ESTUDIANTE: catálogo, carrito, pago, recibo, estado e historial.
Responsable: Eliú Barrera (login y push: José Daniel Lizca). Lee primero el `CLAUDE.md` raíz.

Stack: Kotlin + Jetpack Compose · MVVM · Navigation Compose · Retrofit + OkHttp · DataStore.

- Solo consume la API REST según `docs/openapi.yaml`. No inventes endpoints.
- El JWT se guarda en DataStore y lo añade un interceptor de OkHttp.
- Emulador: la API está en `http://10.0.2.2:8080`; tráfico sin HTTPS solo en el build `debug`.
- `google-services.json` nunca se sube al repositorio.
- Hecho = `./gradlew assembleDebug testDebugUnitTest` en verde.
