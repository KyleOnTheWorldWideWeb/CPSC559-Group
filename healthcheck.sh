#!/bin/bash
# Check if Java process is running
if pgrep -f "java.*addressingserver.jar" > /dev/null; then
  exit 0
else
  exit 1
fi