FROM gradle:9.6.1-jdk25 AS build
RUN mkdir -p /home/gradle/project
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN --mount=type=cache,id=playly-ai-gradle-jdk25,target=/home/gradle/.gradle/caches,uid=1000,gid=1000,sharing=locked \
    gradle --no-daemon --build-cache check installDist

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /home/gradle/project/build/install/playly-ai/ ./
USER 10001:10001
ENTRYPOINT ["/app/bin/playly-ai"]
