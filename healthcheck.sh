#!/bin/sh
HOST=localhost # since this is an internal check, route message internally.
PORT=5050 # this is the port we are using for responding to health-checks

# Try opening a TCP connection using bash
timeout 1 bash -c "</dev/tcp/$HOST/$PORT" 2>/dev/null

# this will return true if ANY type of response comes back - even "I'm crashed, the network is on fire, and we're all gonna dieeeee....!!!"
if [ $? -eq 0 ]; then
  exit 0  # success, container is healthy
else
  echo "Healthcheck failed: cannot connect to $HOST:$PORT"
  exit 1  # failure, Docker marks container as unhealthy
fi
