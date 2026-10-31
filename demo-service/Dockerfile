FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY config-core/pom.xml config-core/
COPY config-sdk/pom.xml config-sdk/
COPY config-server/pom.xml config-server/
COPY demo-service/pom.xml demo-service/
COPY load-tests/pom.xml load-tests/

# Copy sources
COPY config-core/src config-core/src
COPY config-sdk/src config-sdk/src
COPY demo-service/src demo-service/src
RUN mvn -B -pl demo-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/demo-service/target/demo-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
