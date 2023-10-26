FROM eclipse-temurin:21_35-jdk-ubi9-minimal
# @sha256:f53c2fb071bb78506e568290025a7ab634cf219f41578c1657f202cb077f8536

ENV LANGUAGE='en_US:en'

WORKDIR /deployments

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 target/quarkus-app/lib/ ./lib/
COPY --chown=185 target/quarkus-app/*.jar ./
COPY --chown=185 target/quarkus-app/app/ ./app/
COPY --chown=185 target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="quarkus-run.jar"

ENTRYPOINT java $JAVA_OPTS -jar $JAVA_APP_JAR

