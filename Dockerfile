# ==========================================
# ETAPA 1: Compilacion nativa (GraalVM)
# ==========================================
# Cloudflare Containers solo ejecuta imagenes linux/amd64, asi que las dos etapas
# se fijan a esa plataforma en vez de heredar la del equipo que construye.
# native-image:25 = Oracle Linux 10 + GraalVM 25 + native-image. Tiene que ser 25
# porque el pom declara <java.version>25</java.version>: con la imagen 21 el
# compilador corta con "release version 25 not supported".
FROM container-registry.oracle.com/graalvm/native-image:25 AS build

# La imagen de Oracle trae el JDK y native-image, pero no Maven, y el repo no
# tiene .mvn/wrapper/ para caer en ./mvnw. Se baja el binario aparte.
ARG MAVEN_VERSION=3.9.11
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      | tar -xz -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn

WORKDIR /app

# Copiar configuracion de Maven con mirror para evitar rate-limits de Maven Central
COPY .mvn ./.mvn
COPY pom.xml ./

# Las dependencias se bajan antes de copiar src/ para que un cambio de codigo no
# invalide esta capa y la build siguiente no vuelva a descargar todo Maven.
RUN mvn -B --settings .mvn/settings.xml dependency:go-offline

COPY src ./src

# El perfil native lo aporta spring-boot-starter-parent: engancha process-aot del
# spring-boot-maven-plugin y configura el native-maven-plugin. No hace falta
# declarar nada de eso en el pom. native:compile forkea el ciclo hasta package,
# asi que compile, process-aot y jar corren antes de invocar native-image.
#
# imageName: sin esto el binario sale como target/labflowapi, que es el artifactId.
#
# Los tres ARG de abajo existen solo por la memoria del equipo que compila.
# native-image pide "more than 7.67GB" para esta app en la fase de compilacion:
# con menos heap se pasa el ~70% del tiempo en GC hasta cortar con
# "OutOfMemoryError: GC overhead limit exceeded".
#
# Los defaults estan calibrados para el Docker Build Cloud builder (~16.5 GB de
# RAM): 12g de heap deja de sobra el piso de 7.67GB y ~4.5 GB para el OS + gcc.
# --parallelism sube el pico de memoria (por defecto usa todos los hilos) y -Ob
# baja el pico bastante, a cambio de un binario menos optimizado.
#
# Para construir en un equipo con poca RAM (p.ej. Docker Desktop limitado a 8 GB)
# hay que bajar el heap y el paralelismo a mano:
#   docker build --build-arg NATIVE_XMX=6g --build-arg NATIVE_PARALLELISM=4 \
#                --platform linux/amd64 -t labflow-api .
ARG NATIVE_XMX=12g
ARG NATIVE_PARALLELISM=6
ARG NATIVE_OPT_LEVEL=b

RUN mvn -B --settings .mvn/settings.xml -Pnative -Dmaven.test.skip=true \
      -DimageName=labflow-api \
      -DbuildArgs="-O${NATIVE_OPT_LEVEL},--parallelism=${NATIVE_PARALLELISM},-J-Xmx${NATIVE_XMX}" \
      native:compile


# ==========================================
# ETAPA 2: Imagen final
# ==========================================
# El binario nativo queda enlazado dinamicamente contra la glibc 2.39 de Oracle
# Linux 10, asi que la etapa final es OL10 y no Alpine: musl con libc6-compat no
# ejecuta binarios de GraalVM, y OL9 tampoco sirve porque trae glibc 2.34.
FROM container-registry.oracle.com/os/oraclelinux:10-slim
WORKDIR /app

# La app no necesita privilegios: corre como usuario propio.
RUN useradd -r -U -s /sbin/nologin labflow

COPY --from=build --chown=labflow:labflow /app/target/labflow-api /app/labflow-api
RUN chmod +x /app/labflow-api

USER labflow

# Spring lee SERVER_PORT y lo mapea a server.port; tiene que coincidir con el
# defaultPort de la clase Container en worker/index.ts.
ENV SERVER_PORT=8080
EXPOSE 8080

# Sin JAVA_TOOL_OPTIONS ni flags de GC: aca no hay JVM. El heap del binario
# nativo se acota, si hace falta, pasandole -Xmx al propio ejecutable.
ENTRYPOINT ["/app/labflow-api"]

