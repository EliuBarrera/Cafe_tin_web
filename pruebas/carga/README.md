# Prueba de carga: hora punta (RNF-02, RNF-03)

`pedido-y-pago.js` simula la hora punta con [k6](https://k6.io): cada estudiante virtual ve el
catálogo, crea un pedido de una unidad y lo paga, una y otra vez, durante casi dos minutos.

| Requisito | Qué se mide | Umbral |
|---|---|---|
| RNF-02 | `pedido_y_pago`: desde que se crea el pedido hasta que el pago responde | p95 < 5 s |
| RNF-03 | El stock nunca queda negativo; quien llega tarde a la última unidad recibe 409 | 0 respuestas inesperadas |

El informe estima ≈ 100 pedidos por hora en hora punta. Con 40 estudiantes simultáneos (valor por
defecto) la prueba genera muchos más, así que el margen es amplio.

## Cómo correrla

**Nunca contra producción:** crea estudiantes y pedidos reales. El script se niega si `BASE_URL` es de
Render, Vercel o Neon.

1. Una base de prueba con los datos de V2 y la API en perfil `dev`. Sin tocar tu rama de Neon:

   ```bash
   docker run -d --name pg-carga -p 15432:5432 -e POSTGRES_PASSWORD=carga -e POSTGRES_DB=neondb postgres:16
   cd api && ./mvnw -q package -DskipTests
   DB_URL=jdbc:postgresql://localhost:15432/neondb DB_USER=postgres DB_PASSWORD=carga \
     JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n') java -jar target/cafetin-api-0.0.1-SNAPSHOT.jar
   ```

2. En otra terminal, k6 en Docker (no hace falta instalarlo):

   ```bash
   docker run --rm --network host -v "$PWD/pruebas/carga:/carga" grafana/k6:1.3.0 \
     run /carga/pedido-y-pago.js
   ```

   Variables opcionales: `-e BASE_URL=http://localhost:8080` y `-e ESTUDIANTES=40`.

3. k6 termina con código distinto de 0 si algún umbral falla.

4. Para limpiar: detén la API y ejecuta `docker rm -f pg-carga`.

## Cómo leer el resultado

- `pedido_y_pago` (p95): debe estar muy por debajo de 5 s.
- `pedidos_pagados` y `pedidos_sin_stock`: con el stock del seed (unas 180 unidades) el catálogo se
  agota durante la prueba. Que aparezcan 409 es lo esperado: muestra que la base rechaza vender lo
  que no hay (`CHECK cantidad_disponible >= 0`).
- `respuestas_inesperadas`: cualquier 4xx distinto de 409 o cualquier 5xx. Debe ser 0 %.

Para medir solo el rendimiento, con carga sostenida y sin que se agote el catálogo, sube el stock
antes de correrla (el `search_path` es obligatorio: los triggers nombran las tablas sin esquema):

```bash
docker exec pg-carga psql -U postgres -d neondb -c "SET search_path = cafetin; UPDATE inventario SET cantidad_disponible = 100000"
```

En Render (plan gratuito, 512 MB) los tiempos serán mayores que en local y la primera petición tarda
mientras la API y Neon despiertan.

## Resultados (08/11/2026)

Local: laptop de 8 núcleos y 8 GB de RAM, Java 25, PostgreSQL 16 en Docker, 40 estudiantes simultáneos.

| Corrida | Pagados | `pedido_y_pago` p95 | Errores | Base de datos después |
|---|---|---|---|---|
| Stock del seed (165 unidades) | 165 | 72 ms | 0 % | Stock 0, ningún producto negativo, una fila de `pago` por pedido, una sola alerta `STOCK_AGOTADO` por producto |
| Stock alto (carga sostenida) | 1628 en ~2 min (≈ 14,7/s) | 35 ms | 0 % | Unidades descontadas = pagos (1628) |

RNF-02 y RNF-03 se cumplen con amplio margen en local. Falta repetirla contra Render con una rama de
Neon de prueba (no `main`) para tener el dato del despliegue real.
