#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

APP_DIR="$BASE_DIR/app"
LOG_DIR="$BASE_DIR/log"
CONF_DIR="$BASE_DIR/conf"
PID_FILE="$BASE_DIR/java_app.pid"

APP_NAME="java_app"
JAR_PATTERN="java_app-*.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

log_detail() {
    echo -e "${BLUE}[DETAIL]${NC} $1"
}

# Function to check if app is running
get_status() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            echo "running:$pid"
        else
            echo "stopped:stale_pid"
        fi
    else
        echo "stopped:no_pid"
    fi
}

# Function to get process information
get_process_info() {
    local pid=$1
    local start_time=$(ps -o lstart= -p "$pid" 2>/dev/null | sed 's/^ *//')
    local cpu_mem=$(ps -o %cpu,%mem= -p "$pid" 2>/dev/null | sed 's/^ *//')
    local cmd=$(ps -o args= -p "$pid" 2>/dev/null)
    
    echo "  PID: $pid"
    echo "  Started: $start_time"
    echo "  CPU/Memory: $cpu_mem"
    echo "  Command: $cmd"
}

# Function to get JAR information
get_jar_info() {
    local jar_files=("$APP_DIR"/$JAR_PATTERN)
    local valid_jars=()
    
    # Filter out non-existent files
    for jar in "${jar_files[@]}"; do
        if [ -f "$jar" ]; then
            valid_jars+=("$jar")
        fi
    done
    
    local jar_count=${#valid_jars[@]}
    
    if [ $jar_count -eq 0 ]; then
        log_error "No JAR file found"
    elif [ $jar_count -gt 1 ]; then
        log_warn "Multiple JAR files found:"
        for jar in "${valid_jars[@]}"; do
            log_warn "  - $(basename "$jar")"
        done
    else
        log_detail "JAR file: $(basename "${valid_jars[0]}")"
        log_detail "JAR size: $(ls -lh "${valid_jars[0]}" | awk '{print $5}')"
        log_detail "JAR modified: $(ls -l "${valid_jars[0]}" | awk '{print $6, $7, $8}')"
    fi
}

# Function to check configuration files
check_config() {
    local config_file="$CONF_DIR/application.properties"
    local log4j_file="$CONF_DIR/log4j2.xml"
    
    if [ -f "$config_file" ]; then
        log_detail "Config file: ✓ application.properties"
    else
        log_error "Config file: ✗ application.properties (missing)"
    fi
    
    if [ -f "$log4j_file" ]; then
        log_detail "Log config: ✓ log4j2.xml"
    else
        log_error "Log config: ✗ log4j2.xml (missing)"
    fi
}

# Function to show recent log entries
show_recent_logs() {
    local stdout_log="$LOG_DIR/java_app.stdout"
    local app_log="$LOG_DIR/log-bnp.log"
    
    if [ -f "$stdout_log" ]; then
        log_detail "Recent stdout log (last 5 lines):"
        tail -n 5 "$stdout_log" 2>/dev/null | sed 's/^/    /'
    fi
    
    if [ -f "$app_log" ]; then
        log_detail "Recent application log (last 3 lines):"
        tail -n 3 "$app_log" 2>/dev/null | sed 's/^/    /'
    fi
}

# Main status check
echo "=========================================="
echo "         $APP_NAME Status Report"
echo "=========================================="

status=$(get_status)
status_state=$(echo "$status" | cut -d: -f1)
status_detail=$(echo "$status" | cut -d: -f2)

case "$status_state" in
    "running")
        log_info "$APP_NAME is RUNNING"
        get_process_info "$status_detail"
        echo
        ;;
    "stopped")
        if [ "$status_detail" = "stale_pid" ]; then
            log_warn "$APP_NAME is STOPPED (stale PID file found)"
            log_warn "Cleaning up stale PID file..."
            rm -f "$PID_FILE"
        else
            log_error "$APP_NAME is STOPPED"
        fi
        echo
        ;;
esac

echo "=========================================="
echo "         Configuration Status"
echo "=========================================="
get_jar_info
echo
check_config

echo
echo "=========================================="
echo "         Log Information"
echo "=========================================="
if [ -d "$LOG_DIR" ]; then
    log_detail "Log directory: $LOG_DIR"
    log_detail "Log files:"
    ls -la "$LOG_DIR" 2>/dev/null | grep -v "^total" | sed 's/^/    /' || log_warn "No log files found"
    echo
    show_recent_logs
else
    log_warn "Log directory does not exist: $LOG_DIR"
fi

echo
echo "=========================================="

# Exit with appropriate code
if [ "$status_state" = "running" ]; then
    exit 0
else
    exit 1
fi