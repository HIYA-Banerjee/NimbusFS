FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/nimbusfs-master-1.0.0-shaded.jar master.jar
EXPOSE 9000
VOLUME ["/root/.nimbusfs"]
ENTRYPOINT ["java", "-jar", "master.jar"]
