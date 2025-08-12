#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

APP_DIR="$BASE_DIR/app"
LOG_DIR="$BASE_DIR/log"
CONF_DIR="$BASE_DIR/conf"
PID_FILE="$BASE_DIR/hermes_app.pid"

APP_NAME="hermes_app"
JAR_PATTERN="hermes_app-*.jar"
CONFIG_FILE="$CONF_DIR/application.properties"

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
    if is_running; then
        local pid=$(cat "$PID_FILE")
        log_info "Stopping $APP_NAME (PID: $pid)..."
        kill "$pid"
        
        # Wait for graceful shutdown (max 30 seconds)
        local count=0
        while [ $count -lt 30 ] && ps -p "$pid" > /dev/null 2>&1; do
            sleep 1
            count=$((count + 1))
        done
        
        if ps -p "$pid" > /dev/null 2>&1; then
            log_warn "Graceful shutdown failed, forcing termination..."
            kill -9 "$pid"
            sleep 2
        fi
        
        rm -f "$PID_FILE"
        log_info "$APP_NAME stopped successfully"
        return 0
    else
        log_info "$APP_NAME is not running"
        return 1
    fi
}

# Function to find JAR file
find_jar() {
    local jar_files=("$APP_DIR"/$JAR_PATTERN)
    local valid_jars=()
    
    # Filter out non-existent files (in case no match found)
    for jar in "${jar_files[@]}"; do
        if [ -f "$jar" ]; then
            valid_jars+=("$jar")
        fi
    done
    
    local jar_count=${#valid_jars[@]}
    
    if [ $jar_count -eq 0 ]; then
        log_error "No JAR file found matching pattern $JAR_PATTERN in $APP_DIR"
        return 1
    elif [ $jar_count -gt 1 ]; then
        log_error "Multiple JAR files found in $APP_DIR:"
        for jar in "${valid_jars[@]}"; do
            log_error "  - $(basename "$jar")"
        done
        log_error "Please ensure only one version is present"
        return 1
    else
        echo "${valid_jars[0]}"
        return 0
    fi
}

# Function to start the application
start_app() {
    # Check if already running and stop if needed
    if is_running; then
        log_info "$APP_NAME is already running, stopping it first..."
        stop_app
    fi
    
    # Find the JAR file
    local jar_file
    jar_file=$(find_jar)
    if [ $? -ne 0 ]; then
        return 1
    fi
    
    log_info "Found JAR file: $(basename "$jar_file")"
    
    # Verify configuration files exist
    if [ ! -f "$CONFIG_FILE" ]; then
        log_error "Configuration file not found: $CONFIG_FILE"
        return 1
    fi
    
    # Create log directory if it doesn't exist
    mkdir -p "$LOG_DIR"
    
    # Prepare JVM arguments
    local jvm_args="-server"
    jvm_args="$jvm_args -Xms512m -Xmx1024m"
    jvm_args="$jvm_args -Dspring.config.location=file:$CONFIG_FILE"
    
    log_info "Starting $APP_NAME..."
    log_info "JAR: $(basename "$jar_file")"
    log_info "Config: $CONFIG_FILE"
    log_info "Log Directory: $LOG_DIR"
    
    # Start the application in background
    nohup java $jvm_args -jar "$jar_file" > "$LOG_DIR/hermes_app.stdout" 2>&1 &
    local pid=$!
    
    # Save PID
    echo $pid > "$PID_FILE"
    
    # Wait a moment and check if the process is still running
    sleep 2
    if ps -p "$pid" > /dev/null 2>&1; then
        log_info "$APP_NAME started successfully (PID: $pid)"
        return 0
    else
        log_error "$APP_NAME failed to start"
        rm -f "$PID_FILE"
        log_error "Check logs in $LOG_DIR for details"
        return 1
    fi
}

# Main execution
case "${1:-start}" in
    start)
        start_app
        ;;
    restart)
        start_app
        ;;
    *)
        echo "Usage: $0 {start|restart}"
        echo "  start   - Start the application (stops if already running)"
        echo "  restart - Alias for start"
        exit 1
        ;;
esac