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
- WINDOWS: (TODO: verify this on a windows machine)
```powershell
(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike "127.*" }).IPAddress
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
