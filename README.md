
## DISTRIBUTED IRC-STYLE CHAT SYSTEM


A fault-tolerant, horizontally scalable chat system built in Java (JDK 21),
deployed via Docker. Designed around distributed systems principles including
gossip-based replication, dynamic client routing, and automatic failure recovery.



### Overview

---------------------------------------------------------

This project implements a multi-server IRC-style chat platform where no single
server is a point of failure. Clients connect through a centralized addressing
layer that routes them to available chat servers. Chat servers replicate messages
between each other automatically, and the system recovers from node failures
without direct operator intervention.

### Architecture

---------------------------------------------------------

The system has three distinct components:

Addressing Servers
- Manage client routing and maintain a registry of active chat servers.
- Utilize a Primary/Replica model with automatic leader election to ensure
high availability and state persistence.

Chat Servers
- Accept client connections and persist chat logs locally
- Propagate messages to peer servers using a gossip-based push-pull
protocol with vector timestamp ordering

Clients
- Connect via a command-line styled GUI
- Automatically reconnect and resend unacknowledged messages on
server failure


### Key Technical Features

---------------------------------------------------------

#### Non-Blocking I/O

Java NIO server sockets allow for high client concurrency without assigning a dedicated 
thread per client. Each connection maintains its own write queue and partial-read buffer, 
correctly handling cases where messages are written or received across multiple I/O events.

#### Client Routing

The Primary Addressing Server routes new clients to the most populated chat server 
below its maximum capacity. 
Routing decisions are based on the live network state — 
only servers with a formally registered and active NIO connection to the Primary are 
eligible, preventing clients from being directed to stale registry entries.


#### Gossip-Based Message Replication

Vector timestamps maintain message ordering across all nodes without requiring a global lock.
Used in conjunction with a gossip-based push-pull protocol, messages are efficiently 
propagated across chat servers — ensuring a consistent view of the chat log without 
relying on a centralized broadcast or coordinator.

#### Message Delivery Guarantees

Clients cache unacknowledged messages and retry on reconnection.
Servers ACK all received messages to confirm delivery.

#### Heartbeat Failure Detection

Chat servers and addressing servers each monitor their own peers via adaptive 
RTT-based timeouts, minimizing false positives on degraded networks. When an
unresponsive node is detected by its peer, a notification is sent
to the Primary Addressing Server via the peer's persistent NIO connection, 
which also serves as a passive liveness check for the Primary. 
All failure information converges at the Primary, which maintains an authoritative view 
of the network topology — centralizing failure awareness while keeping detection itself 
decentralized and domain specific.

#### Automatic Fault Recovery

Failed chat servers rebuild state by pulling the full chat log from peers on reconnection,
requiring no persistent storage and keeping servers stateless and simple to deploy.
The system recovers without operator intervention;
any clients connected to a failed server are automatically rerouted,
ensuring transparent service.

#### Consistency Guarantees
The Primary Addressing Server enforces strong consistency across replicas by ensuring any
topology change is acknowledged by all replicas before being committed.
This guarantees that any view of the network topology —
whether read from the primary or a replica —
reflects the same state, preventing split-brain scenarios during failover.

#### Leader Election and Failover
Addressing Server failover is managed via a Bully-based election protocol, where the 
replica with the highest network ID assumes the role of Primary upon detecting a failure. 
Rather than rebuilding the network from scratch, chat servers and replicas 
instead send a synchronization message to the new Primary, 
preserving the network state across the transition. 

The new Primary validates any synchronization request against its internal records before 
acceptance, preventing unauthorized or stale processes from rejoining the topology. After the
network settles, the Primary performs an internal registry audit, 
purging any nodes that failed to synchronize within a set window.
This ensures the system transitions to a new leader with minimal disruption while 
maintaining the integrity of the network.


### Tech Stack

---------------------------------------------------------
- Java JDK 21
- Java NIO (non-blocking sockets)
- JSON serialization for inter-process communication
- Docker: platform-agnostic containerized deployment services

### How to Run the System

---------------------------------------------------------

###### Build the images

- Open a terminal and navigate to the systems root directory

```bash
docker compose build
```


###### Spin up the primary addressing server and two replicas:
```bash
docker compose --profile addressingserver --profile addressingserver-backup --scale addressingserver-backup=2 up --build
```

- Deploy two chat servers:
```bash
docker compose --profile chatserver up -d --scale chatserver=2
```

- Each client uses an interactive terminal for its GUI; to initiate a chat-room with two participants open two terminals
  and enter the following command in each:
```bash
docker compose run client
```


--------------------------------------------------------------------------------
DESIGN DOCUMENT
--------------------------------------------------------------------------------

A full system design document including sequence diagrams, architecture
diagrams, and fault tolerance specifications is available at:

[Add link to PDF once updated and improved]

================================================================================