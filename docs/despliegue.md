# Despliegue: Neon (main) + Render (API) + Vercel (panel)

Pasos en orden para publicar la API y el panel. Responsable: José Daniel Lizca (Fase 7, adelantada).
Los valores reales (contraseñas, secreto JWT) van solo en los paneles de Neon, Render y Vercel:
nunca en el repositorio ni en el chat del equipo.

## 0. Antes de empezar

- `main` debe tener el `Dockerfile` (`api/Dockerfile`) y la versión de Java con la que se quiere
  desplegar: Render construye la imagen desde `main`.
- La rama `main` de Neon es **producción**. Nadie desarrolla contra ella.

## 1. Neon: cambiar la contraseña y copiar la conexión

1. Neon → proyecto `cafetin` → **Branches** → `main` → **Roles** → `neondb_owner` → **Reset password**.
   Hazlo antes del primer despliegue: la contraseña anterior estuvo en un `.env` local.
2. **Connect** en la rama `main`, con **Connection pooling desmarcado** (host **sin** `-pooler`).
   Flyway no funciona con la conexión *pooled*.
3. Anota el host, el usuario y la contraseña nueva para el paso 2.

## 2. Render: crear el servicio de la API

**New → Web Service** desde el repositorio de GitHub:

| Campo | Valor |
|---|---|
| Root Directory | `api` |
| Runtime | Docker |
| Branch | `main` |
| Region | Ohio (la misma región que Neon) |
| Health Check Path | `/actuator/health/liveness` |

Variables de entorno (**Environment**):

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<host-de-neon-main-sin-pooler>/neondb?sslmode=require` |
| `DB_USER` | `neondb_owner` |
| `DB_PASSWORD` | la contraseña nueva del paso 1 |
| `JWT_SECRET` | uno **nuevo**, generado con `openssl rand -base64 48` (una sola línea, mínimo 32 bytes) |
| `CORS_ORIGINS` | `https://cafe-tin-web.vercel.app` |
| `CAFETIN_RECUPERACION_ENLACEBASE` | `https://cafe-tin-web.vercel.app/restablecer?token=` |

No reutilices el `JWT_SECRET` de tu `.env`: si se filtra, cualquiera puede firmar tokens de dueño.

**Health check:** `/actuator/health/liveness` responde `{"status":"UP"}` sin token. No consulta la base
a propósito: Render lo llama cada pocos segundos y, si tocara Neon, la base nunca se suspendería y se
gastaría la cuota gratuita. `/actuator/health` (sin `/liveness`) sí revisa la conexión a Neon: sirve para
diagnosticar a mano, no para Render.

Al primer arranque, Flyway crea el esquema `cafetin` en Neon `main` con las migraciones de
`db/migration` (V1, V10…). El perfil `prod` **no** carga los datos de prueba (V2): la base queda vacía.

## 3. Crear el primer dueño

Con la base vacía nadie puede iniciar sesión, y los trabajadores solo los crea un dueño (RF-02).
El dueño se crea a mano una sola vez.

1. Genera el hash BCrypt de su contraseña (pide la contraseña sin mostrarla):

   ```bash
   cd api && ./mvnw verify        # solo si nunca compilaste la API en este equipo
   scripts/hash-contrasena.sh     # imprime algo como $2a$10$…
   ```

2. Neon → rama `main` → **SQL Editor**. Reemplaza los valores entre `<>` y ejecuta:

   ```sql
   BEGIN;

   WITH nuevo AS (
       INSERT INTO cafetin.usuario (nombre, apellidos, correo, contrasena, telefono, tipo_usuario)
       VALUES ('<Nombre>', '<Apellidos>', '<correo@dominio.pe>', '<hash $2a$10$… del paso 1>',
               '<9 dígitos o NULL>', 'DUENO')
       RETURNING id_usuario
   )
   INSERT INTO cafetin.dueno (id_usuario, cargo, ruc)
   SELECT id_usuario, 'Propietario', NULL FROM nuevo;    -- ruc: NULL o 11 dígitos entre comillas

   COMMIT;
   ```

3. Comprueba que quedó bien:

   ```sql
   SELECT u.id_usuario, u.correo, u.tipo_usuario, d.cargo
   FROM cafetin.usuario u JOIN cafetin.dueno d USING (id_usuario);
   ```

Si el `INSERT` falla, `BEGIN … COMMIT` evita que quede un `usuario` sin su fila en `dueno`.

## 4. Probar la API

```bash
curl -s -X POST https://<servicio>.onrender.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"correo":"<correo del dueño>","contrasena":"<su contraseña>"}'
```

Debe responder un JSON con `token` y `"rol":"DUENO"`. La primera petición puede tardar: el plan
gratuito de Render apaga el servicio sin tráfico y Neon suspende la base.

## 5. Vercel: apuntar el panel a la API

1. Vercel → proyecto del panel → **Settings → Environment Variables**:
   `VITE_API_URL=https://<servicio>.onrender.com`.
2. **Deployments → Redeploy**: Vite lee la variable al compilar, no al abrir la página.
3. Entra a `https://cafe-tin-web.vercel.app`, inicia sesión con el dueño y crea un trabajador.

Si el navegador muestra un error de CORS, revisa que `CORS_ORIGINS` en Render tenga exactamente el
dominio de Vercel, con `https://` y sin `/` al final.

## Pendientes conocidos

- **Recuperar contraseña (RF-03) no envía nada en producción:** `EnvioRecuperacionLog` solo escribe
  el enlace en el log y en `prod` está desactivado. Falta un envío real (por ejemplo, correo).
