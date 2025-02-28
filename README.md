# CPSC 559 - Distributed Systems Group Project
Repository for Group 16 

## Project Name: 
IRC-style Chat - You can't spell chat without "hat"!

### Project Description:
Our project is a distributed IRC-style chat system designed for secure, real-time messaging with decentralized
server replication. Using a client-server architecture, users connect to a network of servers
that store and propagate chat logs using a gossip-based replication model, ensuring eventual consistency
without reliance on a central authority. Servers periodically exchange updates using vector timestamps
to track message order and resolve conflicts. Clients communicate over TCP with an ACK-based delivery
mechanism to guarantee message reliability. If a server fails, clients automatically reconnect to another
available node. When a server rejoins, it synchronizes with peers to recover missing messages. The
system is lightweight, terminal-based, and prioritizes fault tolerance, scalability, and privacy, ensuring
messages remain encrypted end-to-end and are never stored in plaintext.

###### Contributors:
 - Sloman, Aidan
 - Briggs, Cole
 - Robitaille, Chloe
 - West, Kyle
 - Virdi, Parmeet

#### To-Do List:

##### Setup Dockerfiles for:

- [ ] Chat Server
- [ ] Addressing Server
- [ ] Client Stub
- [ ] The SQL Database?

#### Gradle
 - The Gradle build file is written in Kotlin
 - Ensure you have the most recent version of Gradle installed (8.13) as versions < 8.10 do not support JDK23
 - We are using a Gradle Wrapper to ensure consistency across team projects
		- Use the command "gradlew" instead of "gradle"
 
[Build Script Explanation](https://docs.gradle.org/current/userguide/writing_build_scripts.html)

[Create "tasks" for build automation](https://docs.gradle.org/current/userguide/tutorial_using_tasks.html)

[Gradle plugin for managing Docker builds](https://bmuschko.github.io/gradle-docker-plugin/current/user-guide/#introduction)

[Gradle plugin for managing Docker compose](https://github.com/avast/gradle-docker-compose-plugin)


#### Docker 
 - Setup:
	- Ensure that your device is registered with your Docker account so that you can use it.
 - Gradle tasks are used to build Docker images from Dockerfiles.
 - You can interact with Docker by:
	- commands directly in the terminal -> docker <some_command>
	- the Docker Desktop UI
 - Docker images are the blueprints used to create Docker containers.
	- All of these images are stored in a VM that Docker manages (the virtual disk file is docker_data.vhdx - do not modify it).
    - "docker image" will list all the images on your local machine. Or youc an view them using Docker Desktop
 - Docker containers are isolated runtime environments that include our compiled Java source files and dependencies.
		*the Dockerfile contains comments explaining how we copy the source code into the Docker container*
 - Dockerfiles are placed within the root of each module in our repository.
 - Dockerfiles can be modified — the Gradle tasks should not need to be modified unless the build process changes.

 
 
###### We currently have three main Docker containers:

Chat Server (cpsc559/team16-chatserver)
Addressing Server (cpsc559/team16-addressingserver)
Client (cpsc559/team16-client)

Gradle automatically builds the Docker images for each component when we run:

	gradle buildChatServerImage
	gradle buildAddressingServerImage
	gradle buildClientImage

To create the Containers:

    gradle createChatServerContainer
    gradle createAddressingServerContainer
    gradle createClientContainer


To start the Containers:

	gradle startChatServerContainer
	gradle startAddressingServerContainer
	gradle startClientContainer
 
 Q: Where is our compiled Java code? 
 A: Inside the Docker container. Each container includes the necessary files to run the service.

 Q: What happens if a container crashes?
 A: We can restart it manually (gradle startChatServerContainer), or use Docker Compose to auto-restart.
 
 Q: Do we need to rebuild the images every time?
 A: Only if code changes. Otherwise, you can just restart the existing containers like any compiled program.