#!/bin/bash

# Start Xvfb
Xvfb :99 -screen 0 1280x1024x24 -ac &

# Wait for Xvfb to start
sleep 1

# Run the application
exec java -jar /app/build/libs/linkfixer-1.0-SNAPSHOT.jar