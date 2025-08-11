#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

PID_FILE="$BASE_DIR/hermes_app.pid"
APP_NAME="hermes_app"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if app is running
is_running() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            return 0  # Running
        else
            # PID file exists but process is not running, remove stale PID file
            rm -f "$PID_FILE"
            return 1  # Not running
        fi
    else
        return 1  # Not running
    fi
}

# Function to stop the application
stop_app() {
    if ! is_running; then
        log_info "$APP_NAME is not running"
        return 0
    fi
    
    local pid=$(cat "$PID_FILE")
    log_info "Stopping $APP_NAME (PID: $pid)..."
    
    # Send SIGTERM for graceful shutdown
    kill "$pid"
    
    # Wait for graceful shutdown (max 30 seconds)
    local count=0
    while [ $count -lt 30 ] && ps -p "$pid" > /dev/null 2>&1; do
        echo -n "."
        sleep 1
        count=$((count + 1))
    done
    echo
    
    # Check if process is still running
    if ps -p "$pid" > /dev/null 2>&1; then
        log_warn "Graceful shutdown failed after 30 seconds, forcing termination..."
        kill -9 "$pid"
        sleep 2
        
        # Final check
        if ps -p "$pid" > /dev/null 2>&1; then
            log_error "Failed to stop $APP_NAME (PID: $pid)"
            return 1
        fi
    fi
    
    # Remove PID file
    rm -f "$PID_FILE"
    log_info "$APP_NAME stopped successfully"
    return 0
}

# Main execution
stop_app
exit $?