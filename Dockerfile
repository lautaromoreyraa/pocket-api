# =============================================================================
#  POCKET API — imagen de la aplicación
#
#  Build en dos etapas: la primera compila con Maven, la segunda solo lleva
#  el JRE y el jar. La imagen final no incluye Maven ni el código fuente.
#
#  Construir:  docker build -t pocket-api .
#  Correr:     docker compose --profile full up -d --build
# =============================================================================


# -----------------------------------------------------------------------------
#  ETAPA 1 — compilación
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Las dependencias se copian y descargan primero, en una capa aparte.
# Mientras el pom.xml no cambie, Docker reutiliza esta capa y el build
# posterior es mucho más rápido.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B


# -----------------------------------------------------------------------------
#  ETAPA 2 — ejecución
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Usuario sin privilegios: si alguien logra ejecutar código en el contenedor,
# no lo hace como root.
RUN groupadd -r pocket && useradd -r -g pocket pocket

COPY --from=build /build/target/*.jar app.jar
RUN chown pocket:pocket app.jar

USER pocket

EXPOSE 8080

# Deja que la JVM respete los límites de memoria del contenedor en vez de
# leer los de la máquina anfitriona.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
