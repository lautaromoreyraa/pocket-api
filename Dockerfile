

FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn clean package -DskipTests -B -q

RUN cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extraido


FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system --gid 10001 pocket \
 && useradd --system --uid 10001 --gid pocket --no-create-home --shell /usr/sbin/nologin pocket \
 && mkdir -p /app/tmp \
 && chown pocket:pocket /app/tmp

RUN keytool -list -cacerts -storepass changeit > /tmp/cacerts.txt \
 && grep -i emsign /tmp/cacerts.txt | sed 's/, [A-Z][a-z][a-z] [0-9].*//' \
  | while read -r alias; do keytool -delete -cacerts -storepass changeit -alias "$alias" || exit 1; done \
 && ! keytool -list -cacerts -storepass changeit | grep -qi emsign \
 && rm /tmp/cacerts.txt

COPY --from=build /build/extraido/dependencies/ ./
COPY --from=build /build/extraido/spring-boot-loader/ ./
COPY --from=build /build/extraido/snapshot-dependencies/ ./
COPY --from=build /build/extraido/application/ ./

USER 10001

EXPOSE 8080

ENV TZ=UTC

ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxDirectMemorySize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

ENV JDK_JAVA_OPTIONS="-Djava.io.tmpdir=/app/tmp"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${PORT:-8080}/actuator/health" | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
