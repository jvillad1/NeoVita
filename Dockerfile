FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .

# Use the project's Gradle wrapper (8.10.2) instead of the image's gradle (8.10)
# so the build version is pinned/reproducible. Logs are left verbose so a failed
# first deploy is debuggable from Railway's build logs.

# Build wasmJs production bundle
RUN ./gradlew :webApp:wasmJsBrowserDistribution --no-daemon

# Copy web app into server resources so it's bundled in the fat JAR
RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

# Build server fat JAR (now includes the web app)
RUN ./gradlew :server:buildFatJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
