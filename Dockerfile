# Build stage
FROM gradle:8.12-jdk21 AS build

WORKDIR /app

# Copy gradle files first for better layer caching
COPY gradle ./gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/libs.versions.toml ./gradle/

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew installDist --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

# Install yt-dlp, ffmpeg, and Deno (required for YouTube extraction)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        ffmpeg \
        curl \
        unzip && \
    curl -fsSL https://deno.land/install.sh | sh && \
    mv /root/.deno/bin/deno /usr/local/bin/ && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    apt-get remove -y curl unzip && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /root/.deno

WORKDIR /app

# Copy the built application from build stage
COPY --from=build /app/build/install/youtube-podcast ./

# Expose the application port
EXPOSE 8080

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
CMD ["sh", "-c", "./bin/youtube-podcast"]
