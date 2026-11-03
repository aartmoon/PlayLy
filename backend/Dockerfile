FROM --platform=$BUILDPLATFORM gradle:9.6.1-jdk25 AS build
RUN mkdir -p /home/gradle/project
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle OpenApi.yml ./
COPY --chown=gradle:gradle backend/ ./backend/
WORKDIR /home/gradle/project/backend
RUN --mount=type=cache,id=playly-core-gradle-jdk25,target=/home/gradle/.gradle/caches,uid=1000,gid=1000,sharing=locked \
    gradle --no-daemon --build-cache check bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /home/gradle/project/backend/build/libs/playly-core.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
