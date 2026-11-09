#!/usr/bin/env bash
# Imprime el hash BCrypt de una contraseña, igual al que guarda la API (BCryptPasswordEncoder).
# Sirve para crear a mano el primer dueño en la rama main de Neon (ver docs/despliegue.md).
# Pide la contraseña sin mostrarla, así no queda en el historial de la terminal.
# Requiere un JDK y haber compilado la API una vez (./mvnw verify) para tener las librerías en ~/.m2.
set -euo pipefail

m2="${HOME}/.m2/repository"
crypto=$(find "$m2/org/springframework/security/spring-security-crypto" -name 'spring-security-crypto-*.jar' ! -name '*sources*' 2>/dev/null | sort -V | tail -1)
logging=$(find "$m2/commons-logging/commons-logging" -name 'commons-logging-*.jar' ! -name '*sources*' 2>/dev/null | sort -V | tail -1)
if [[ -z "$crypto" || -z "$logging" ]]; then
	echo "Faltan librerías en ~/.m2: ejecuta antes 'cd api && ./mvnw verify'." >&2
	exit 1
fi

read -rsp 'Contraseña (mínimo 8 caracteres): ' contrasena; echo >&2
read -rsp 'Repítela: ' repetida; echo >&2
if [[ "$contrasena" != "$repetida" ]]; then
	echo "No coinciden." >&2
	exit 1
fi
if (( ${#contrasena} < 8 )); then
	echo "Debe tener al menos 8 caracteres (la API exige lo mismo)." >&2
	exit 1
fi

printf '%s\n' \
	'System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(System.getenv("CONTRASENA")));' \
	'/exit' |
	CONTRASENA="$contrasena" jshell --feedback silent --class-path "$crypto:$logging" 2>/dev/null |
	grep -o '\$2[aby]\$[0-9][0-9]\$[./0-9A-Za-z]\{53\}'
