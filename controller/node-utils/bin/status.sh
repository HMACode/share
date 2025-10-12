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
echo "Server Status"
echo "==============================================="

if [ ! -f "$SERVER_SCRIPT" ]; then
    echo -e "${RED}Error: server.py not found at $SERVER_SCRIPT${NC}"
    exit 1
fi

SERVER_RUNNING=false
SERVER_PID=""

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        if ps -p "$PID" -o cmd= | grep -q "server.py"; then
            SERVER_RUNNING=true
            SERVER_PID=$PID
        fi
    fi
fi

if [ "$SERVER_RUNNING" = false ]; then
    PIDS=$(pgrep -f "python.*server.py")
    if [ -n "$PIDS" ]; then
        SERVER_RUNNING=true
        SERVER_PID=$(echo "$PIDS" | head -n 1)
    fi
fi

if [ "$SERVER_RUNNING" = true ]; then
    echo -e "${GREEN}Status: RUNNING${NC}"
    echo -e "${GREEN}PID: $SERVER_PID${NC}"
    
    if [ -f "$LOG_FILE" ]; then
        LOG_SIZE=$(du -h "$LOG_FILE" | cut -f1)
        echo -e "${GREEN}Log file: $LOG_FILE ($LOG_SIZE)${NC}"
    fi
    
    UPTIME=$(ps -p "$SERVER_PID" -o etime= | tr -d ' ')
    echo -e "${GREEN}Uptime: $UPTIME${NC}"
    
    MEM=$(ps -p "$SERVER_PID" -o rss= | tr -d ' ')
    MEM_MB=$((MEM / 1024))
    echo -e "${GREEN}Memory: ${MEM_MB} MB${NC}"
    
    echo -e "${GREEN}URL: http://localhost:8080/info${NC}"
    
    if command -v curl > /dev/null 2>&1; then
        echo ""
        echo -e "${YELLOW}Testing connection...${NC}"
        if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/info | grep -q "200"; then
            echo -e "${GREEN}Server is responding correctly${NC}"
        else
            echo -e "${RED}Server is not responding${NC}"
        fi
    fi
else
    echo -e "${RED}Status: NOT RUNNING${NC}"
    
    if [ -f "$LOG_FILE" ]; then
        echo -e "${YELLOW}Last 10 lines of log:${NC}"
        tail -n 10 "$LOG_FILE"
    fi
fi

echo "==============================================="

