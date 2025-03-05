# Use a single base image
FROM eclipse-temurin:23-jdk

# Set the working directory in the container
WORKDIR /app

# Copy the fat JAR file to the container
COPY build/libs/chatserver.jar /app/chatserver.jar

# Copy the .env file to the container
COPY .env /app/.env

# Install bash
RUN apt-get update && apt-get install -y bash

# Expose the port the application runs on
ENV PORT 2424
EXPOSE ${PORT}

# Run the application
ENTRYPOINT ["bash", "-c", "source /app/.env && java -jar chatserver.jar"]