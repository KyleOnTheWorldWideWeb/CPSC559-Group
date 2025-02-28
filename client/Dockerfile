# ✅ Use a single base image
FROM eclipse-temurin:23-jdk

# ✅ Set the working directory inside the container
WORKDIR /app

# ✅ Copy the compiled JAR file from Gradle (assuming the JAR is built)
COPY build/libs/client.jar /app/client.jar

# ✅ Ensure the correct package path for the main class
CMD ["java", "-cp", "/app/client.jar", "io.github.cpsc559.team16.client.Client"]
