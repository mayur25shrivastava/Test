FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG MODULE
RUN mvn -q -pl ${MODULE} -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
ARG MODULE
COPY --from=build /src/${MODULE}/target/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
