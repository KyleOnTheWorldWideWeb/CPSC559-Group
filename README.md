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

###### We currently have four modules that constitute the project:
- Chat Server (cpsc559/team16-chatserver)
- Addressing Server (cpsc559/team16-addressingserver)
- Client (cpsc559/team16-client)
- Utilities
# SETUP
  ### **How To Configure this Docker-compose.yml for Primary Addressing Server**
- First, retrieve your ipv4 address.
- WINDOWS: 
  - If you're on wifi you want the ipv4 address for WIFI
```powershell
ipconfig
```
- MAC:
```zsh
ipconfig getifaddr en0
```
- LINUX:
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1 | awk '{print $2}' | head -n 1
```

## Setting environment variables
- If you're running the primary address server:
```bash
PRIMARY_ADDRESS_SERVER_IP={insert-your-ipv4-here}
PUBLIC_ADDRESS={insert-your-ipv4-here}
AS_CLIENT_PORT=49800
AS_REPLICA_PORT=49801
AS_CHATSERVER_PORT=49802
CS_PORT=2424
CS_PEER_PORT=2425
CS_ADDRSERVER_PORT=49802
CLIENT_PORT=3000
AS_ROLE=PRIMARY
```
- If you are running a backup address server you need to get the Primary address server IP from whoever is running the primary.
```bash
PRIMARY_ADDRESS_SERVER_IP={insert-primary-address-ip-here}
PUBLIC_ADDRESS={insert-your-ipv4-here}
AS_CLIENT_PORT=49800
AS_REPLICA_PORT=49801
AS_CHATSERVER_PORT=49802
CS_PORT=2424
CS_PEER_PORT=2425
CS_ADDRSERVER_PORT=49802
CLIENT_PORT=3000
# Change your Role to BACKUP
AS_ROLE=BACKUP 
```

# How to start this up
- Addressing server
  - First make sure the primary is running if you're not the primary.
```bash
docker compose --profile addressingserver up --build
```
- Chat Server
```bash
docker compose --profile chatserver up --build
```
- Client
```bash
docker compose --profile client create --build
# After the build you need to run it like
docker compose run --rm --service-ports client
```
# Make sure to close the containers down 
For every container you run 
```bash
docker compose --profile {profile-you-have-running} down
```