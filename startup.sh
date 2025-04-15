#!/bin/bash
echo "----> Addressing server environment variables:"
env | sort

# Wait for DNS to stabilize
echo "Waiting for DNS records to stabilize..."
sleep 5

# Get list of all container IPs in the service
echo "Discovering service instances..."
CONTAINER_LIST=$(getent hosts tasks.addressing_addressingserver || echo "")
echo "Found instances: $CONTAINER_LIST"

# Get my own IP
MY_IP=$(hostname -i | awk '{print $1}')
echo "My IP: $MY_IP"

# Sort the IPs to ensure consistent selection across all containers
SORTED_IPS=$(echo "$CONTAINER_LIST" | awk '{print $1}' | sort)
echo "Sorted IPs: $SORTED_IPS"

# Get the first IP in sorted order - this will be our primary
PRIMARY_IP=$(echo "$SORTED_IPS" | head -1)
echo "Selected primary IP: $PRIMARY_IP"

# Determine if I am the primary
if [ "$MY_IP" = "$PRIMARY_IP" ]; then
  echo "I AM THE PRIMARY SERVER"
  export AS_ROLE=PRIMARY
else
  echo "I AM A BACKUP SERVER"
  export AS_ROLE=BACKUP
  export PRIMARY_HOST=$PRIMARY_IP
fi

# For external communications
export HOST_ADDRESS=$MY_IP

echo "Final role: $AS_ROLE"
echo "Host address: $HOST_ADDRESS"
echo "Primary host: ${PRIMARY_HOST:-N/A}"

# Execute the application
exec java -jar /app/addressingserver.jar