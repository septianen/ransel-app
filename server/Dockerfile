# Packages the already-built distribution (./gradlew :server:installDist).
# Gradle is not run inside Docker because :shared also configures the Android plugin (needs the SDK);
# CI builds the distribution first, then this image only copies it.
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 ransel
WORKDIR /app
COPY build/install/server/ /app/
USER ransel
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["/app/bin/server"]
