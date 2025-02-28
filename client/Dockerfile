# Use a single base image
FROM eclipse-temurin:23-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the compiled JAR file from Gradle (assuming the JAR is built)
COPY build/libs/client.jar /app/client.jar

# Ensure the correct package path for the main class
#CMD ["java", "-cp", "/app/client.jar", "io.github.cpsc559.team16.client.Client"]


# Defines the default program run by the Docker Container
# You can override CMD by appending a new command to docker run, e.g. -docker run --rm -p 8080:8080 client:latest <relative_path.jar>
# You can start an interactive shell inside the container with docker run -it client:latest sh
CMD ["java", "-jar", "/app/client.jar"]