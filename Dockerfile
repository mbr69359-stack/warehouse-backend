FROM maven:3.8.8-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx256m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8", "-Dserver.tomcat.uri-encoding=UTF-8", "-jar", "app.jar"]
