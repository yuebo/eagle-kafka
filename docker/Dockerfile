FROM openjdk:8-jdk-alpine
VOLUME /tmp
ENV JAVA_OPS "-Xmx256m"
ARG JAR_FILE
COPY app.jar app.jar
ENTRYPOINT  java $JAVA_OPS -Dspring.profiles.active=$ACTIVE_PROFILE -Djava.security.egd=file:/dev/./urandom -jar ke.war