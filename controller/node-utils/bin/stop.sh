#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="${SCRIPT_DIR}/server.pid"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "==============================================="
echo "Server Stop Script"
echo "==============================================="

is_server_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            if ps -p "$PID" -o cmd= | grep -q "server.py"; then
                return 0
            fi
        fi
        rm -f "$PID_FILE"
    fi
    
    if pgrep -f "python.*server.py" > /dev/null 2>&1; then
        return 0
    fi
    
    return 1
}

stop_server() {
    echo -e "${YELLOW}Checking for running server...${NC}"
    
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            echo -e "${YELLOW}Stopping server (PID: $PID)...${NC}"
            kill "$PID" 2>/dev/null
            
            for i in {1..5}; do
                if ! ps -p "$PID" > /dev/null 2>&1; then
                    break
                fi
                sleep 1
            done
            
            if ps -p "$PID" > /dev/null 2>&1; then
                echo -e "${YELLOW}Force killing server...${NC}"
                kill -9 "$PID" 2>/dev/null
                sleep 1
            fi
        fi
        rm -f "$PID_FILE"
    fi
    
    PIDS=$(pgrep -f "python.*server.py")
    if [ -n "$PIDS" ]; then
        echo -e "${YELLOW}Stopping remaining server processes...${NC}"
        for PID in $PIDS; do
            kill "$PID" 2>/dev/null
            sleep 1
            if ps -p "$PID" > /dev/null 2>&1; then
                kill -9 "$PID" 2>/dev/null
            fi
        done
    fi
    
    echo -e "${GREEN}Server stopped successfully.${NC}"
}

if is_server_running; then
    stop_server
else
    echo -e "${YELLOW}Server is not running.${NC}"
fi

echo "==============================================="
echo -e "${GREEN}Done!${NC}"
echo "==============================================="

