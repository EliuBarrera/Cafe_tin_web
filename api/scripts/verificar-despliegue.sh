#!/usr/bin/env bash
# Comprueba una API desplegada (Fase 7) sin modificar nada: health check, base de datos, seguridad,
# CORS para el panel y que Swagger esté apagado en producción.
#
#   api/scripts/verificar-despliegue.sh https://<servicio>.onrender.com [https://cafe-tin-web.vercel.app]
#
# Si Render dormía la API, la primera petición puede tardar unos minutos: el script espera.
set -uo pipefail

api="${1:?Uso: $0 URL_DE_LA_API [ORIGEN_DEL_PANEL]}"
api="${api%/}"
panel="${2:-https://cafe-tin-web.vercel.app}"
fallos=0

revisar() { # descripción, condición ya evaluada (0 = bien)
	if [[ "$2" == 0 ]]; then echo "  ✓ $1"; else echo "  ✗ $1"; fallos=$((fallos + 1)); fi
}

echo "Esperando a que $api responda…"
for _ in $(seq 1 60); do
	curl -sf --max-time 10 "$api/actuator/health/liveness" >/dev/null && break
	sleep 5
done

liveness=$(curl -s --max-time 30 "$api/actuator/health/liveness")
[[ "$liveness" == *'"UP"'* ]]; revisar "health check de Render (/actuator/health/liveness) = UP" $?

health=$(curl -s --max-time 30 "$api/actuator/health")
[[ "$health" == *'"status":"UP"'* ]]; revisar "conexión a la base (/actuator/health) = UP" $?

estado=$(curl -s -o /tmp/verificar-login.json -w '%{http_code}' --max-time 30 -X POST "$api/api/auth/login" \
	-H 'Content-Type: application/json' -d '{"correo":"no.existe@cafetin.pe","contrasena":"no-es-la-clave"}')
[[ "$estado" == 401 ]] && grep -q '"NO_AUTENTICADO"' /tmp/verificar-login.json
revisar "login con datos falsos → 401 NO_AUTENTICADO (la API y la base responden)" $?
rm -f /tmp/verificar-login.json

estado=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$api/api/reportes/ingresos")
[[ "$estado" == 401 ]]; revisar "ruta del dueño sin token → 401" $?

origen=$(curl -s -D - -o /dev/null --max-time 30 -X OPTIONS "$api/api/auth/login" \
	-H "Origin: $panel" -H 'Access-Control-Request-Method: POST' | tr -d '\r' \
	| awk -F': ' 'tolower($1) == "access-control-allow-origin" {print $2}')
[[ "$origen" == "$panel" ]]; revisar "CORS permite el panel ($panel)" $?

estado=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$api/swagger-ui.html")
[[ "$estado" == 404 || "$estado" == 401 ]]; revisar "Swagger apagado en producción" $?

if [[ "$api" == https://* ]]; then revisar "HTTPS (RNF-04)" 0; else echo "  · HTTPS: no aplica a $api"; fi

echo
if ((fallos == 0)); then echo "Todo en orden."; else echo "$fallos comprobaciones fallaron."; fi
exit $((fallos > 0))
