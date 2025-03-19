# Stage 1: Build the application using Gradle with JDK 17
FROM gradle:7.6-jdk17 AS build

# Set the working directory
WORKDIR /app

# Copy all project files to the container
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Run the Gradle build command
RUN ./gradlew build --no-daemon

# Stage 2: Create the runtime image with the necessary dependencies
FROM openjdk:24-slim-bookworm AS runtime

# Set the working directory
WORKDIR /app

# Install required tools for runtime (git, cmake, etc.)
RUN apt-get update && apt-get install -y \
    git \
    cmake \
    gcc-arm-none-eabi \
    libstdc++-arm-none-eabi-newlib \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*


# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set the entry point to run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]