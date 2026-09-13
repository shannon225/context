FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY libs/ ./libs/
COPY pom.xml ./
COPY build.sh ./
RUN CONTEXT_MAVEN_REPO=/root/.m2/repository ./build.sh javapot
RUN mvn -B -Ppackage dependency:go-offline || true

COPY src/ ./src/

ARG VERSION=""
RUN if [ -n "$VERSION" ]; then \
      mvn -B org.codehaus.mojo:versions-maven-plugin:2.17.1:set \
          -DnewVersion="$VERSION" -DgenerateBackupPoms=false; \
    fi

ARG MVN_FLAGS=""
RUN mvn -B -Ppackage clean package ${MVN_FLAGS}

FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-venv \
 && rm -rf /var/lib/apt/lists/* \
 && python3 -m venv /opt/pyisopep \
 && /opt/pyisopep/bin/pip install --no-cache-dir --upgrade pip \
 && /opt/pyisopep/bin/pip install --no-cache-dir pyIsoPEP

ENV PATH=/opt/pyisopep/bin:$PATH
ENV TMPDIR=/tmp
ENV JAVA_OPTS=""

COPY --from=build /build/target/context-*.jar /opt/context/context.jar

RUN pyisopep --help > /dev/null && java -jar /opt/context/context.jar --help > /dev/null

WORKDIR /data

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/context/context.jar \"$@\"", "--"]
CMD ["--help"]
