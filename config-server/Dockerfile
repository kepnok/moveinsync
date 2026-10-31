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
COPY config-server/src config-server/src
RUN mvn -B -pl config-server -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/config-server/target/config-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
