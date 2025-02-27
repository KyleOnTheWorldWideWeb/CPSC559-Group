# Use Ubuntu as the base image
FROM ubuntu:latest

# Install necessary packages: OpenJDK, dependencies, and cleanup
RUN apt-get update && apt-get install -y \
    openjdk-23-jdk \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory inside the container
WORKDIR /app

# Copy the client's source code and configuration files into the containers
COPY src/io/github/cpsc559/team16/client /app/src
COPY config /app/config

# Compile the Java client (only if necessary) 
RUN javac -d /app/bin /app/src/Client.java

# Define the entry point to run the Java client
CMD ["java", "-cp", "/app/bin", "io.github.src.cpsc559.team16.client.Client"]

