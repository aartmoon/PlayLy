FROM gradle:9.3.1-jdk25 AS build
RUN mkdir -p /home/gradle/project
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon check jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/playly-ai.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
