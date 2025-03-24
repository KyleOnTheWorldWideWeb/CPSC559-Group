# A guide on how to use the new Docker compose file

## Why?
We want to ensure that our programs are being compiled with the same dependencies that the run time image is supplying it with. By encapsulating the build process inside a multi-stage build process we're ensuring that the compiled program will be free of any dependency issues no matter which OS the container is built and ran in. As we're all using different operating systems it's important that our code be built inside these containers rather than locally and then stuffed into a container that may or may not be compatible with the run time image we are using.


# Docker Profiles 
https://docs.docker.com/build/building/multi-stage/
https://docs.docker.com/compose/how-tos/profiles/
## How do we make docker files for specific tasks
Now that we're using multi-stage docker files we are elevating environmental variable declaration to the docker compose files. This streamlines spinning up test containers and simplifies the gradle task files.
Here's the current layout of a docker file to run a specific gradle command. If you want to change the gradle command you could create a Dockerfile.{insertclevernamehere} then do the following:
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

```yml

services:  # Defines the services (Docker containers) that will be run.
  . . .
  {insertcustomnamehere}: # Name of the service
      image: {insertcustomnamehere}:latest
      container_name: {insertcustomnamehere}
      # Assign a custom profile to the service or include it another profile so that it is spun up together with services existing in that profile.
      profiles: ["{insertcustomnamehere}", "{example-multi-service-profile}"] 
      build:
        dockerfile: Dockerfile.{insertclevernamehere}
      environment:
      # assign environment variables here and choose which ports to expose

      networks:
      - chat_network
      # OR OPTIONALLY: if you want to assign it to a static ip-address in the chat_network subnet    
      #   chat_network:
      #     ipv4_address: 255.255.0.2
    # deploy: # Optional declaration of the number of replicas one can create of a given service.
       # replicas: {insert-number-of-replicas-here}
    # The below is NON-OPTIONAL if you want the shell to be interactive. Looking at you Client side folks (>.>)
    # tty: true        # You need these two lines to make the client interactive
    # stdin_open: true # Non-optional for interactive shell input
    
```

# How can i connect to a container from outside the subnet?
Any container with explictly exposed ports in the docker-compose file can be reached from localhost : portnumber
```yml
    # example:
    ports: ["2424:2424","2525:2525"]
```
The issue is that you can only have one of that container running at a time if you want it to be facing the internet or accessible on any machines on your regular network.

You can get around this by giving each container a different port range. However, for testing purposes you only need to run the containers in the local docker subnet so this won't be an issue.

You can telnet an exposed container with the following command this way:
```bash
telnet localhost ${insert-port-here}
```

# How do we run these bad boys?
Profiles are key here. Let's say we want to run all containers associated with a specific profile. 
```bash
docker compose --profile ${example-profile} up --build
```
If you don't want to rebuild a container and just run it you can leave out --build
## ** IF YOU KEEP GETTING ADDRESS ALREADY IN USE ERRORS. **
ALWAYS run docker compose down after you kill containers. If that doesn't clear the issue then go through and delete your containers, images, and builds then try it again.
Sometimes docker doesn't close containers when you ctrl + C them so they are just running idle in the background using that valuable port and address space.

Each container should have a singular profile associated with it if you only want to spin one of that type of container up.

## Shutting them down
you can shut down specific containers by profile using 
```bash
docker compose --profile ${example-profile} down
```
Shut down all profiles
```bash
docker compose down
```
## *** Warning the below commands will NUKE ALL your docker containers. Not just the ones you created in this Project ***
stop and remove all containers
```bash
docker stop $(docker ps -q)
docker rm $(docker ps -a -q)
```
