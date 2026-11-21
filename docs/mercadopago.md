# Cobro con Mercado Pago

La API cobra con **Checkout Pro** en la cuenta de Mercado Pago de cada cafetín (modelo marketplace:
el dinero llega al cafetín y la plataforma puede quedarse una comisión). Mientras
`CAFETIN_PAGOS_PASARELA` no sea `mercadopago`, se usa la pasarela simulada y nada de esto hace falta.

> Estado al 21/11/2026: primer pago de punta a punta aprobado en producción, en **modo prueba** y con
> Mercado Pago **Colombia (MCO)**, porque la cuenta de Mercado Pago del equipo es colombiana. Por eso
> `CAFETIN_PAGOS_MONEDA=COP` y en el checkout no aparece Yape. Para cobrar en Perú, alguien con cuenta de
> Mercado Pago Perú crea otra aplicación y se cambian `MP_CLIENT_ID`, `MP_CLIENT_SECRET`,
> `MP_WEBHOOK_SECRET` y la moneda a `PEN`. No hay que tocar código. Las pruebas de Perú también necesitan
> esa cuenta: no se pueden armar desde la cuenta colombiana (ver *Problemas conocidos*). Pendiente: el
> equipo creará una cuenta de Mercado Pago Perú.

## 1. La aplicación de Mercado Pago (una sola, la de la plataforma)

La aplicación es la del **marketplace**: con su Client ID y Client Secret la API pide a cada cafetín que
la autorice (OAuth). Mercado Pago no mezcla cuentas reales con cuentas de prueba, así que **en pruebas la
aplicación también tiene que ser de un usuario de prueba**:

| Entorno | Aplicación |
|---|---|
| Pruebas (la que usa Render hoy) | App espejo `5194962408562556`, de la cuenta de prueba `3862056228` |
| Cobro real | **GestionCafetin** (`2512482686914298`), de la cuenta real del equipo |

Con GestionCafetin y cafetines de prueba, Checkout Pro termina en *"Oh, no, algo anduvo mal"* al pagar
(probado el 21/11/2026).

Para configurar la app espejo, entra en una **ventana de incógnito** a
[Tus integraciones](https://www.mercadopago.com.co/developers/panel/app) con la cuenta de prueba
`3862056228` (usuario y contraseña en *Cuentas de prueba* de GestionCafetin) y abre la aplicación:

1. **Editar → Redirect URL**: `https://cafetin-api-7vnp.onrender.com/api/mercadopago/oauth/retorno`
   (o `<tu túnel>/api/mercadopago/oauth/retorno`). Tiene que coincidir con `CAFETIN_URL_PUBLICA_API`.
2. **Credenciales de producción** → `MP_CLIENT_ID` (Client ID) y `MP_CLIENT_SECRET`. Aunque digan
   "producción", no mueven dinero real: la aplicación es de un usuario de prueba. La *Public Key* y el
   *Access Token* no se usan: el token sale de la cuenta de cada cafetín al conectar.
3. **Webhooks → Configurar notificaciones**: URL `https://cafetin-api-7vnp.onrender.com/api/mercadopago/webhook`,
   evento **Pagos**. Copia la **clave secreta** a `MP_WEBHOOK_SECRET`. (Cada preferencia ya manda su
   propia `notification_url` con el cafetín; esta configuración es la clave de la firma.)

Para el cobro real se hacen los mismos tres pasos en GestionCafetin y cada cafetín conecta su cuenta real.

Las credenciales van en tu `.env` o en el panel de Render, **nunca** en el repositorio ni en el chat.

## 2. Variables

| Variable | Valor |
|---|---|
| `CAFETIN_PAGOS_PASARELA` | `mercadopago` (`simulada` por defecto) |
| `CAFETIN_PAGOS_COMISION` | `0` por defecto; `0.05` = 5 % para la plataforma (`marketplace_fee`) |
| `CAFETIN_PAGOS_MONEDA` | `COP` con la aplicación de prueba colombiana; `PEN` en Perú |
| `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_WEBHOOK_SECRET` | ver el paso 1 |
| `CAFETIN_CIFRADO_LLAVE` | `openssl rand -base64 32`. Cifra los tokens de cada cafetín; si cambia, cada dueño reconecta |
| `CAFETIN_URL_PUBLICA_API` | URL pública de la API (Render o un túnel) |
| `CAFETIN_URL_PANEL` | panel al que vuelve el dueño tras conectar |

Con `CAFETIN_PAGOS_PASARELA=mercadopago` la API **no arranca** si falta una credencial o la llave.

## 3. Usuarios de prueba

Hacen falta **tres** cuentas de prueba del mismo país (*Cuentas de prueba* de GestionCafetin, o
`/mercadopago:mp-test-setup`):

| Papel | Cuenta | Para qué |
|---|---|---|
| Marketplace | `3862056228` | Dueña de la app espejo (paso 1). No puede conectarse como cafetín |
| Cafetín | un vendedor de prueba creado aparte | El dueño lo conecta desde el panel → **Cobros**. Uno por cafetín |
| Estudiante | `3862056230` (comprador) | Inicia sesión en Checkout Pro y paga |

Prueba en una ventana de incógnito para no usar tu sesión real de Mercado Pago, y si el teléfono tiene
la app de Mercado Pago, abre el enlace de pago en el navegador: la app de Mercado Pago usa la cuenta con
la que esté abierta. Tarjetas de prueba: `/mercadopago:mp-test-cards` (el nombre del titular decide el
resultado: `APRO` aprobado, `OTHE` rechazado).

Fuera de las pruebas, el estudiante **no necesita cuenta** de Mercado Pago: puede pagar como invitado
con tarjeta.

## 4. Flujo

1. **Dueño conecta** (panel → Cobros): `POST /api/cafeterias/mia/mercadopago/conectar` → abre
   `urlAutorizacion` → Mercado Pago vuelve a `/api/mercadopago/oauth/retorno` → la API guarda los tokens
   cifrados y redirige al panel con `?mercadopago=conectado`. El enlace vale 10 minutos y es de un solo uso.
2. **Estudiante paga**: `POST /api/pedidos/{id}/pago` deja el pago `PENDIENTE` y devuelve `pago.urlPago`.
   La app abre esa URL. Sin cuenta conectada responde 409 *"El cafetín aún no acepta pagos"*.
3. **Vuelta**: Checkout Pro vuelve a `/api/mercadopago/retorno-pago?pedido=…`, que redirige a
   `cafetin://pago/{id}`. La app llama a `POST /api/pedidos/{id}/pago/verificar`.
4. **Confirmación**: el webhook y la verificación hacen lo mismo y son idempotentes. Se consulta el pago
   a Mercado Pago con el token del cafetín, se comprueba que el monto sea el total del pedido y, si está
   aprobado, el pago pasa a `PAGADO` (la base descuenta el stock) y se emite el recibo.
5. **Sin stock al confirmar** (otro pagó la última unidad) o pedido ya cancelado: se **reembolsa** el
   cobro; el pago queda `REEMBOLSADO` y el pedido `CANCELADO`.

## 5. Probar en local

El OAuth y el webhook necesitan una URL pública. Dos opciones:

- Usar la API de Render (apuntando a tu rama de Neon de desarrollo).
- Un túnel hacia `localhost:8080` y poner su URL en `CAFETIN_URL_PUBLICA_API` y en la Redirect URL.

Sin URL pública igual se puede pagar: la preferencia se crea sin `notification_url` y la app confirma
con `/pago/verificar` al volver.

## 6. Problemas conocidos

| Síntoma | Causa | Qué hacer |
|---|---|---|
| *"Oh, no, algo anduvo mal"* al pulsar **Pagar** | La aplicación es de una cuenta real y el cafetín o el comprador son de prueba | Usa la app espejo (paso 1) y tres cuentas de prueba (paso 3) |
| Solo aparece *Dinero disponible* o el pago falla con montos pequeños | Mercado Pago Colombia cobra desde **$ 1.000 COP**, y la API manda el total en soles con moneda `COP` (S/ 11 → $ 11) | Para probar, sube el precio de un producto desde el panel. Con `PEN` en Perú no pasa |
| Checkout Pro no carga o falla en Brave | Brave Shields bloquea los scripts antifraude de Mercado Pago | Desactiva Shields para `mercadopago.com.co` o usa Chrome |
| La app muestra *"Parámetro con formato incorrecto o faltante"* en el catálogo | APK anterior a varios cafetines (`app-v0.1.0`), que pide el catálogo sin `?cafeteria=` | Instala un APK compilado desde `main` |
| Checkout Pro: *"No se puede continuar con este pago. La cuenta del vendedor es de otro país"* | La aplicación del marketplace es de un país y el cafetín o el comprador de otro (probado: app espejo de Colombia con vendedor y comprador de Perú). El OAuth conecta igual, falla al pagar | Aplicación, cafetín y comprador del **mismo país** |
| Una aplicación creada dentro de una cuenta de prueba no muestra *Client Secret* ni *Redirect URL* | El panel de las cuentas de prueba es simplificado: solo da *Public Key* y *Access Token*, que no sirven para OAuth | La aplicación del marketplace se crea en una cuenta real (o es la app espejo que Mercado Pago crea sola) |
