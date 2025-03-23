# A guide on how to use the new Docker compose file
## Why?
We want to ensure that our programs are being compiled with the same dependencies that the run time image is supplying it with. By encapsulating the build process inside a multi-stage build process we're ensuring that the compiled program will be free of any dependency issues no matter which OS the container is built and ran in. As we're all using different operating systems it's important that our code be built inside these containers rather than locally and then stuffed into a container that may or may not be compatible with the run time image we are using.


# Docker Profiles 
## How do we make docker files for specific tasks
Now that we're using multi-stage docker files we are elevating environmental variable declaration to the docker compose files. This streamlines spinning up test containers and simplifies the gradle task files.
Here's the current layout of a docker file to run a specific gradle command. If you want to change the gradle command you could create a Dockerfile.${insertclevernamehere} then do the following:
```dockerfile 
# Stage 1: Building the jar in a gradle container
FROM gradle:8-jdk21 AS build
# Set the working directory
WORKDIR /app/
# Copy the project files
COPY . .
# Build the project with the Gradle wrapper
RUN gradle {insertCustomGradleTaskhere} --no-daemon
# --info command provides potentially useful debug info. Could also run with --debug i believe
# RUN gradle {insertCustomGradleTaskhere} --no-daemon --info 

# Stage 2: Transferring the product to a runtime container
FROM eclipse-temurin:21-jdk
# Set the working directory
WORKDIR /app
# Copy the built jar from the build stage
COPY --from=build /app/{insertApplicationNameHere}/build/libs/{insertApplicationNameHere}.jar /app/{insertApplicationNameHere}.jar
# Define the default program
ENTRYPOINT ["java", "-jar", "{insertApplicationNameHere}.jar"]
```

Now we have our custom dockerfile created we can create a profile specifically for it or group it into another profile in our ./docker-compose.yml file like so:

