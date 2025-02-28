# Use Ubuntu as the base image
FROM ubuntu:latest

# Use an official OpenJDK 23 image (Eclipse Temurin)
FROM eclipse-temurin:23-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the client's source code and configuration files into the containers
# Syntax explained - COPY <source> <destination>
# <source> -> The path to the sourcefiles you want to copy from our project directory (relative to the root)
# <destination> -> The path inside the Docker container you want to copy the source files into

COPY src/io/github/cpsc559/team16/client /app/src
COPY config /app/config

# Compile the Java client (only if necessary) 
RUN javac -d /app/bin /app/src/Client.java

# Define the entry point to run the Java client
CMD ["java", "-cp", "/app/bin", "src.io.github.cpsc559.team16.client.Client"]

