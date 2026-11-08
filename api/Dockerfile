# Imagen de la API para Render (Root Directory: api). Ver "Despliegue" en el plan.

# --- Compilar ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Los tests necesitan Docker (Testcontainers) y ya corren en GitHub Actions en cada PR.
RUN ./mvnw -q -B package -DskipTests

# --- Ejecutar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home cafetin
COPY --from=build /app/target/cafetin-api-*.jar app.jar
USER cafetin
# Render indica el puerto en $PORT. MaxRAMPercentage: el plan gratuito tiene 512 MB.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-8080} -jar app.jar"]
