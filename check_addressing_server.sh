#!/bin/bash
# Check if addressing server is running and responding
curl -s http://localhost:5050 | grep -q "alive"
exit $?