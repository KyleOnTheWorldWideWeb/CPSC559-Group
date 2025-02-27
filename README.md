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
[Build Script Explanation](https://docs.gradle.org/current/userguide/writing_build_scripts.html)
[Create "tasks" for build automation](https://docs.gradle.org/current/userguide/tutorial_using_tasks.html)


#### Docker 
 - Gradle tasks are used to build Docker images from Dockerfiles.
 - Docker images are the blueprints used to create Docker containers.
 - Docker containers are isolated runtime environments that include our compiled Java source files and dependencies.
 - Dockerfiles are placed within a dedicated directory in our repository.
 - Dockerfiles can be modified — the Gradle tasks should not need to be modified unless the build process changes.
 
###### We currently have three main Docker containers:

Chat Server (cpsc559/team16-chatserver)
Addressing Server (cpsc559/team16-addressingserver)
Client (cpsc559/team16-client)

Gradle automatically builds the Docker images for each component when we run:
 - gradle buildChatServerDockerImage
 - gradle buildAddressingServerDockerImage
 - gradle buildClientDockerImage

To start the Containers:
 - gradle startChatServerContainer
 - gradle startAddressingServerContainer
 - gradle startClientContainer
 
 Q: Where is our compiled Java code? 
 A: Inside the Docker container. Each container includes the necessary files to run the service.

 Q: What happens if a container crashes?
 A: We can restart it manually (gradle startChatServerContainer), or use Docker Compose to auto-restart.
 
 Q: Do we need to rebuild the images every time?
 A: Only if code changes. Otherwise, you can just restart the existing containers like any compiled program.