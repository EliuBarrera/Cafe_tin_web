# Imagen de la API para Render (Root Directory: api). Ver docs/despliegue.md.

# --- Compilar ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Los tests necesitan Docker (Testcontainers) y ya corren en GitHub Actions en cada PR.
RUN ./mvnw -q -B package -DskipTests
# Jar descomprimido (app + lib/): arranca más rápido y es lo que pide el caché AOT.
RUN java -Djarmode=tools -jar target/cafetin-api-*.jar extract --destination extraido \
 && mv extraido/cafetin-api-*.jar extraido/cafetin-api.jar

# --- Ejecutar ---
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home cafetin
COPY --from=build /app/extraido ./

# Caché AOT de Java 25 (JEP 483/514/515): un arranque de entrenamiento al construir la imagen deja
# las clases ya cargadas y enlazadas en app.aot, así la API despierta más rápido en el plan gratuito
# de Render. No hay base durante el build: el entrenamiento arranca el contexto sin Flyway ni
# conexión (Hibernate no lee metadatos) y sale al terminar de refrescarlo. Se hace en esta etapa
# para que el caché sea de la misma JVM que lo usa.
# JAVA_OPCIONES va igual en el entrenamiento y en el arranque: el caché guarda objetos del heap en
# formato G1 y con otro tamaño de heap o con SerialGC la JVM no los carga y se niega a arrancar.
# - Heap de 384 MB = 75 % de los 512 MB del plan gratuito.
# - TieredStopAtLevel=1 (solo el compilador C1): con 0,1 CPU, C2 compite con el arranque. Medido con
#   0,5 CPU: 90 s sin optimizar → 42 s con el caché → 22 s con ambos. El tráfico de la cafetería no
#   necesita la velocidad extra de C2 (ver pruebas/carga).
ENV JAVA_OPCIONES="-Xmx384m -XX:+UseG1GC -XX:TieredStopAtLevel=1"
RUN java $JAVA_OPCIONES -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar cafetin-api.jar \
      --spring.profiles.active=prod \
      --spring.datasource.url=jdbc:postgresql://localhost:5432/entrenamiento \
      --spring.flyway.enabled=false \
      --spring.jpa.hibernate.ddl-auto=none \
      --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      --spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      --cafetin.jwt.secreto=entrenamiento-del-cache-aot-no-es-un-secreto-real

USER cafetin
# Render indica el puerto en $PORT. Antes de arrancar se comprueba que el caché cargue (menos de 1 s):
# depende de la memoria del contenedor, que en el build no es la de Render. Si no carga, la API arranca
# sin él, más lenta, en vez de no arrancar.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "CACHE=-XX:AOTCache=app.aot; java $JAVA_OPCIONES -XX:AOTMode=on $CACHE -cp cafetin-api.jar -version >/dev/null 2>&1 || { echo 'Aviso: el caché AOT no carga con JAVA_OPCIONES; se arranca sin él (más lento).' >&2; CACHE=; }; exec java $JAVA_OPCIONES $CACHE -Dserver.port=${PORT:-8080} -jar cafetin-api.jar"]
