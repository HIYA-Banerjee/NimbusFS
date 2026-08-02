FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/nimbusfs-node-1.0.0-shaded.jar node.jar
EXPOSE 9001
VOLUME ["/root/.nimbusfs"]
ENTRYPOINT ["java", "-jar", "node.jar"]
