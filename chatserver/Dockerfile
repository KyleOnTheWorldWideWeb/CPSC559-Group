# Use a single base image
FROM eclipse-temurin:21-jdk

# Set the working directory in the container
WORKDIR /app

# Copy the fat JAR file to the container
COPY build/libs/chatserver.jar /app/chatserver.jar

# Copy the .env file to the container
COPY .env /app/.env

# Install bash
RUN apt-get update && apt-get install -y bash

# Expose the ports the chat server uses
ENV CS_CLIENT_PORT=2424
ENV CS_PEER_PORT=2425
ENV CS_ADDRSERVER_PORT=2426
EXPOSE ${CS_CLIENT_PORT}
EXPOSE ${CS_PEER_PORT}
EXPOSE ${CS_ADDRSERVER_PORT}


# Run the application
ENTRYPOINT ["bash", "-c", "source /app/.env && java -jar chatserver.jar"]