#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_SCRIPT="${SCRIPT_DIR}/server.py"
PID_FILE="${SCRIPT_DIR}/server.pid"
LOG_FILE="${SCRIPT_DIR}/server.log"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "==============================================="
echo "Server Management Script"
echo "==============================================="

if [ ! -f "$SERVER_SCRIPT" ]; then
    echo -e "${RED}Error: server.py not found at $SERVER_SCRIPT${NC}"
    exit 1
fi

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

start_server() {
    echo -e "${YELLOW}Starting server...${NC}"
    
    nohup python3 "$SERVER_SCRIPT" > "$LOG_FILE" 2>&1 &
    
    SERVER_PID=$!
    echo "$SERVER_PID" > "$PID_FILE"
    
    sleep 2
    
    if ps -p "$SERVER_PID" > /dev/null 2>&1; then
        echo -e "${GREEN}Server started successfully!${NC}"
        echo -e "${GREEN}PID: $SERVER_PID${NC}"
        echo -e "${GREEN}Log file: $LOG_FILE${NC}"
        echo -e "${GREEN}Access the server at: http://localhost:8080/info${NC}"
    else
        echo -e "${RED}Failed to start server. Check $LOG_FILE for errors.${NC}"
        rm -f "$PID_FILE"
        exit 1
    fi
}

if is_server_running; then
    echo -e "${YELLOW}Server is already running.${NC}"
    stop_server
    echo ""
fi

start_server

echo "==============================================="
echo -e "${GREEN}Done!${NC}"
echo "==============================================="

