# Packages the already-built distribution (./gradlew :server:installDist).
# Gradle is not run inside Docker because :shared also configures the Android plugin (needs the SDK);
# CI builds the distribution first, then this image only copies it.
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 ransel
# trip document files (a named volume takes this folder's owner when it is first created)
RUN mkdir -p /data/documents && chown ransel /data/documents
WORKDIR /app
COPY build/install/server/ /app/
USER ransel
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["/app/bin/server"]
