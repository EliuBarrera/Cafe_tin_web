# Cobro con Mercado Pago

La API cobra con **Checkout Pro** en la cuenta de Mercado Pago de cada cafetín (modelo marketplace:
el dinero llega al cafetín y la plataforma puede quedarse una comisión). Mientras
`CAFETIN_PAGOS_PASARELA` no sea `mercadopago`, se usa la pasarela simulada y nada de esto hace falta.

> Estado al 19/11/2026: la aplicación de prueba está en **Colombia (MCO)** porque la cuenta de Mercado
> Pago del equipo es colombiana. Por eso `CAFETIN_PAGOS_MONEDA=COP` y en el checkout no aparece Yape.
> Para cobrar en Perú, alguien con cuenta de Mercado Pago Perú crea otra aplicación y se cambian
> `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_WEBHOOK_SECRET` y la moneda a `PEN`. No hay que tocar código.

## 1. La aplicación de Mercado Pago (una sola, la de la plataforma)

Ya existe: **GestionCafetin** (App ID `2512482686914298`, Checkout Pro). En
[Tus integraciones](https://www.mercadopago.com/developers/panel/app) → GestionCafetin:

1. **Editar → Redirect URL**: `https://cafetin-api-7vnp.onrender.com/api/mercadopago/oauth/retorno`
   (o `<tu túnel>/api/mercadopago/oauth/retorno`). Tiene que coincidir con `CAFETIN_URL_PUBLICA_API`.
2. **Credenciales** → `MP_CLIENT_ID` (App ID) y `MP_CLIENT_SECRET`. Para las pruebas, Mercado Pago creó
   además una aplicación espejo en una cuenta de prueba (App ID `5194962408562556`); sus credenciales
   de producción son las de prueba. Si usas esa, la Redirect URL y el webhook se configuran en ella.
3. **Webhooks → Configurar notificaciones**: URL `https://cafetin-api-7vnp.onrender.com/api/mercadopago/webhook`,
   evento **Pagos**. Copia la **clave secreta** a `MP_WEBHOOK_SECRET`. (Cada preferencia ya manda su
   propia `notification_url` con el cafetín; esta configuración es la clave de la firma.)

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

Mercado Pago creó dos al crear la aplicación (ver con `/mercadopago:mp-test-setup` o en *Cuentas de prueba*):

- **Vendedor** (`3862056228`): dueño de la aplicación espejo de prueba.
- **Comprador** (`3862056230`): con él inicia sesión el estudiante en Checkout Pro.

En un marketplace, la cuenta que conecta el cafetín no puede ser la dueña de la aplicación: crea un
vendedor de prueba por cafetín (`/mercadopago:mp-test-setup`) y conéctalo desde el panel. Pendiente de
confirmar en la primera prueba de punta a punta. Tarjetas de prueba: `/mercadopago:mp-test-cards`
(el nombre del titular decide el resultado: `APRO` aprobado, `OTHE` rechazado).

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
