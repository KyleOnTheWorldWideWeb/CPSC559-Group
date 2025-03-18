# >----------BASE DOCKER IMAGE/LAYER(S) WE PULL FROM EXISTING REPOSITORIES------------<
# FROM eclipse-temurin:23-jdk pulls an Image of JDK23 directly from a repository hosted by eclipse
# Now any Java executables (.jar) or source code (.java) copied into the image will use tools from JDK23
# This ensures that any docker container built from this image will use the same version of Java AND run on any machine that has docker.
FROM eclipse-temurin:23-jdk

# Set the working directory inside the container
# This is where Docker will place
WORKDIR /app

# >----------COPYING DATA INTO A DOCKER IMAGE------------<
# COPY is how we move data (programs, databases, source code, etc) from our repository (directory) into the Image we are constructing.
# Copy the compiled JAR file from Gradle <build/libs/client.jar> into the Docker Image </app/client.jar> (assuming the JAR is built)
# The jar in this case is a "fat jar" which contains all the classes in the client module compiled into one (fat) jar file.
COPY build/libs/client.jar /app/client.jar

# "-cp", "/app/client.jar" sets the classpath to /app/client.jar (the fat jar containing all of the classes in the Client module)
# "io.github.cpsc559.team16.client.Client" specifies which class contains the main method we want the container to launch at startup.
#CMD ["java", "-cp", "/app/client.jar", "io.github.cpsc559.team16.client.Client"]


# >----------DOCKER LAYERS------------<
# Layers are useful for a variety of reasons - they make debugging easier (errors can be isolated to a specific layer)
# Docker only updates a layer in an Image if there has been a change to that layer, or a layer that precedes it....
# .... this is done by caching, and reduces the overhead of image generation.

#RUN pip install flask  (this would install the Flask framework in a new "layer" in the Docker Image)

# >----------INSPECTING DOCKER LAYERS FROM TERMINAL------------<
# You must first find the image ID using: $ docker images -a
# Then find the image's layers and their sizes: $ docker history --no-trunc <Image ID>

# Expose the port the client runs on

ENV CLIENT_ADDRSERVER_PORT=49850
ENV CLIENT_CHATSERVER_PORT=49851
EXPOSE ${CLIENT_ADDRSERVER_PORT}
EXPOSE ${CLIENT_CHATSERVER_PORT}



# >----------EXECUTING PROGRAMS IN DOCKER------------<
# CMD defines the default program that Docker executes upon startup.
# You can override CMD by appending a new command to docker run, e.g. -docker run --rm -p 8080:8080 client:latest <relative_path.jar>
# You can start an interactive shell inside the container with docker run -it client:latest sh
CMD ["java", "-jar", "/app/client.jar"]

