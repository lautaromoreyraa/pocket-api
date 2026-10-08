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

# Techo de memoria explícito, no un porcentaje del host.
#
# En un hosting que factura por RAM consumida, MaxRAMPercentage sale caro por
# omisión: la JVM toma su parte de lo que vea disponible y el recolector no
# devuelve lo que ya no usa, así que el proceso se estaciona en el máximo que
# tocó alguna vez y eso es lo que se paga todos los meses.
#
# 256 MB de heap le alcanzan a esta API —los reportes son agregados de SQL, no
# colecciones grandes en memoria, y el audio más largo (10 MB) cabe varias veces
# aunque se codifique en base64 para la IA—. SerialGC evita los hilos y las
# estructuras auxiliares que G1 mantiene, que en un contenedor de un solo core
# no compensan, y TieredStopAtLevel=1 deja sólo el compilador rápido, que ocupa
# menos memoria y alcanza para este tráfico.
#
# Los flags van en JAVA_TOOL_OPTIONS y no en el ENTRYPOINT: un flag escrito en
# la línea de comando le gana a la variable, y así el hosting no podría
# ajustarlos sin un deploy nuevo. Este valor es el piso cuando nadie define la
# variable; en Railway la define el perfil de recursos del proyecto.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxDirectMemorySize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
