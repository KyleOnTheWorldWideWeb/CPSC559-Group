# Use a single base image
FROM eclipse-temurin:23-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the compiled JAR file from Gradle <build/libs/tests.jar> into ...
# ... the Docker Image </app/tests.jar> (assuming the JAR is built)
# The jar in this case is a "fat jar" which contains all the classes in the client module compiled into one (fat) jar file.
COPY build/libs/tests.jar /app/tests.jar

# "-cp", "/app/client.jar" sets the classpath to /app/client.jar (the fat jar containing all of the classes in the Client module)
# "io.github.cpsc559.team16.client.Client" specifies which class contains the main method we want the container to launch at startup.
#CMD ["java", "-cp", "/app/client.jar", "io.github.cpsc559.team16.client.Client"]

# Expose the port the application runs on
ENV CLIENT_PORT=49800
ENV REPLICA_PORT=49801
ENV CHAT_SERVER_PORT=49802
EXPOSE ${CLIENT_PORT}
EXPOSE ${REPLICA_PORT}
EXPOSE ${CHAT_SERVER_PORT}


# Allow execution
RUN chmod +x tests.jar


# Defines the default program run by the Docker Container - this
# You can override CMD by appending a new command to docker run, e.g. -docker run --rm -p 8080:8080 client:latest <relative_path.jar>
# You can start an interactive shell inside the container with docker run -it client:latest sh
CMD ["java", "-jar", "tests.jar"]