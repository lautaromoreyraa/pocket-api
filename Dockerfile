# =============================================================================
#  POCKET API — imagen de la aplicación
#
#  Build en dos etapas: la primera compila con Maven, la segunda sólo lleva
#  el JRE y las capas del jar. La imagen final no incluye Maven ni el código.
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
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn clean package -DskipTests -B -q

# El jar se separa en las capas de Spring Boot. Las dependencias casi nunca
# cambian y quedan en una capa propia: un deploy que sólo toca código mueve
# unos cientos de KB y no el jar entero.
RUN cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extraido


# -----------------------------------------------------------------------------
#  ETAPA 2 — ejecución
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Usuario sin privilegios, con UID fijo y numérico: Kubernetes (runAsNonRoot)
# y Podman lo verifican por número. /app/tmp es lo único en lo que escribe:
# ahí deja Tomcat el audio mientras se recibe, antes de mandarlo a la IA.
RUN groupadd --system --gid 10001 pocket \
 && useradd --system --uid 10001 --gid pocket --no-create-home --shell /usr/sbin/nologin pocket \
 && mkdir -p /app/tmp \
 && chown pocket:pocket /app/tmp

# CVE-2026-91403: las claves de las CA intermedias de eMudhra, que cuelgan de
# las raíces emSign, quedaron comprometidas, y la JVM aceptaría un certificado
# falso firmado con ellas en cualquier llamada HTTPS que haga esta API. Hasta
# que Temurin publique un JDK que las desconfíe, se sacan del almacén de
# confianza; ningún servicio externo que se usa acá encadena a emSign. El
# último chequeo rompe el build si quedó alguna.
RUN keytool -list -cacerts -storepass changeit > /tmp/cacerts.txt \
 && grep -i emsign /tmp/cacerts.txt | sed 's/, [A-Z][a-z][a-z] [0-9].*//' \
  | while read -r alias; do keytool -delete -cacerts -storepass changeit -alias "$alias" || exit 1; done \
 && ! keytool -list -cacerts -storepass changeit | grep -qi emsign \
 && rm /tmp/cacerts.txt

# Las capas quedan de root: el usuario de la app las lee pero no puede
# reescribirlas si alguien logra ejecutar código adentro.
COPY --from=build /build/extraido/dependencies/ ./
COPY --from=build /build/extraido/spring-boot-loader/ ./
COPY --from=build /build/extraido/snapshot-dependencies/ ./
COPY --from=build /build/extraido/application/ ./

USER 10001

EXPOSE 8080

# La JVM toma la zona de TZ; se puede pisar al correr sin reconstruir.
ENV TZ=UTC

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
# no compensan, y TieredStopAtLevel=1 deja sólo el compilador rápido.
#
# Los flags van en JAVA_TOOL_OPTIONS y no en el ENTRYPOINT: un flag escrito en
# la línea de comando le gana a la variable, y así el hosting no podría
# ajustarlos sin un deploy nuevo. Este valor es el piso cuando nadie define la
# variable; en Railway la define el perfil de recursos del proyecto.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxDirectMemorySize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Lo que no es memoria va en otra variable, justamente porque Railway reemplaza
# JAVA_TOOL_OPTIONS completa: si el directorio temporal viviera ahí, el perfil
# se lo llevaría puesto.
ENV JDK_JAVA_OPTIONS="-Djava.io.tmpdir=/app/tmp"

# Railway no lee esta directiva (usa healthcheckPath) y `podman build` en
# formato OCI la descarta; sirve para `docker run`. El compose define la suya.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${PORT:-8080}/actuator/health" | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
