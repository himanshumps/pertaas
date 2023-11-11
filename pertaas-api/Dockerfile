FROM docker.io/maven:3.9.5-eclipse-temurin-21-alpine as build
WORKDIR /apps
ADD src src
ADD pom.xml pom.xml
RUN mvn clean install -B

FROM docker.io/eclipse-temurin:21.0.1_12-jdk-ubi9-minimal

ENV LANGUAGE='en_US:en'

WORKDIR /deployments

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --from=build --chown=185 /apps/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=185 /apps/target/quarkus-app/*.jar ./
COPY --from=build --chown=185 /apps/target/quarkus-app/app/ ./app/
COPY --from=build --chown=185 /apps/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="quarkus-run.jar"

ENTRYPOINT java $JAVA_OPTS -jar $JAVA_APP_JAR

