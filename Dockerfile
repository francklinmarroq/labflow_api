# Cloudflare Containers solo ejecuta imagenes linux/amd64, asi que las dos etapas
# se fijan a esa plataforma en vez de heredar la del equipo que construye.
FROM --platform=linux/amd64 maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Las dependencias se bajan antes de copiar src/ para que un cambio de codigo no
# invalide esta capa y la build siguiente no vuelva a descargar todo Maven.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

# El jar de Spring Boot se parte en capas: dependencias, loader y clases propias
# quedan en directorios distintos. Como solo la ultima cambia entre despliegues,
# el push a Cloudflare sube unos pocos MB en vez de la imagen completa.
RUN java -Djarmode=tools -jar target/labflowapi-*.jar extract --layers --launcher --destination extracted


FROM --platform=linux/amd64 eclipse-temurin:25-jre-alpine
WORKDIR /app

# La app no necesita privilegios: corre como usuario propio.
RUN addgroup -S labflow && adduser -S labflow -G labflow

# El orden importa: de menos a mas cambiante, para aprovechar el cache de capas.
COPY --from=build --chown=labflow:labflow /app/extracted/dependencies/ ./
COPY --from=build --chown=labflow:labflow /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=labflow:labflow /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=labflow:labflow /app/extracted/application/ ./

USER labflow

# MaxRAMPercentage hace que el heap siga al limite del instance_type en vez de
# adivinarlo. SerialGC porque estas instancias tienen medio vCPU o uno: los GC
# paralelos ahi solo agregan hilos que compiten entre si.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Spring lee SERVER_PORT y lo mapea a server.port; tiene que coincidir con el
# defaultPort de la clase Container en worker/index.ts.
ENV SERVER_PORT=8080
EXPOSE 8080

# Un solo ENTRYPOINT: en un Dockerfile el ultimo pisa a los anteriores. Aca no
# hay app.jar, porque el jar se extrajo por capas; el arranque es via JarLauncher.
# UseContainerSupport ya viene activo por defecto en la JVM y MaxRAMPercentage se
# fija arriba en JAVA_TOOL_OPTIONS, asi que no hacen falta como flags sueltos.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
