# Node Utils API Documentation

This document provides information about the available API endpoints and their usage.

## Configuration

Before starting the server, configure the following environment variables:

- `WEBLOGIC_ADMIN_URL`: The base URL of your WebLogic admin server (e.g., `https://admin.example.com:7001`)

Example:
```bash
export WEBLOGIC_ADMIN_URL="https://admin.example.com:7001"
python3 server.py
```

## Endpoints

### 1. GET /info

Returns system statistics including memory usage, disk usage, and log files information.

**Request:**
```bash
curl http://localhost:8080/info
```

**Response Example:**
```json
{
  "memory": {
    "total_ram_gb": 15.54,
    "used_ram_gb": 8.32,
    "available_ram_gb": 7.22,
    "swap_used_gb": 0.0
  },
  "disk": [
    {
      "filesystem": "/dev/sda1",
      "size": "100G",
      "used": "45G",
      "available": "50G",
      "use_percent": 47,
      "mounted_on": "/"
    }
  ],
  "log_files": [
    {
      "path": "/home/rtx/workspace/node-utils/mylogs/app.log",
      "size": "2.5M"
    }
  ]
}
```

---

### 2. GET /logs/tail

Returns the last 20 lines of all log files modified today from the configured logs directory.

**Request:**
```bash
curl http://localhost:8080/logs/tail
```

**Response Example:**
```json
{
  "date": "2025-10-12",
  "files_count": 2,
  "logs": [
    {
      "file": "/home/rtx/workspace/node-utils/mylogs/app.log",
      "tail": "2025-10-12 10:30:00 INFO Application started\n2025-10-12 10:30:01 INFO Processing request\n..."
    },
    {
      "file": "/home/rtx/workspace/node-utils/mylogs/error.log",
      "tail": "2025-10-12 09:15:23 ERROR Connection timeout\n..."
    }
  ]
}
```

**Error Response (Directory not configured):**
```json
{
  "error": "LOGS_DIRECTORY not configured"
}
```

---

### 3. GET /weblogic/info

Returns comprehensive information about WebLogic servers in the cluster, their state, deployments, and deployment states.

**Prerequisites:**
- `WEBLOGIC_ADMIN_URL` environment variable must be configured
- WebLogic admin server must be accessible
- Authentication: Uses basic auth with username `admin` and password `admin`
- SSL verification is disabled to support self-signed certificates

**Request:**
```bash
curl http://localhost:8080/weblogic/info
```

**Response Example:**
```json
{
  "servers": {
    "all": {
      "body": {
        "items": [
          {
            "name": "Node1",
            "state": "RUNNING",
            "health": "HEALTH_OK"
          },
          {
            "name": "Node2",
            "state": "RUNNING",
            "health": "HEALTH_OK"
          }
        ]
      }
    },
    "Node1": {
      "name": "Node1",
      "state": "RUNNING",
      "health": "HEALTH_OK",
      "activatedThreadCount": 5,
      "queueLength": 0
    },
    "Node2": {
      "name": "Node2",
      "state": "RUNNING",
      "health": "HEALTH_OK",
      "activatedThreadCount": 3,
      "queueLength": 0
    }
  },
  "deployments": {
    "body": {
      "items": [
        {
          "name": "MyApplication",
          "state": "STATE_ACTIVE",
          "type": "war",
          "targets": ["Node1", "Node2"]
        },
        {
          "name": "MyService",
          "state": "STATE_ACTIVE",
          "type": "ear",
          "targets": ["Node1", "Node2"]
        }
      ]
    }
  },
  "timestamp": "2025-10-12T10:30:00.123456"
}
```

**Error Response (URL not configured):**
```json
{
  "error": "WEBLOGIC_ADMIN_URL not configured"
}
```

**Error Response (Connection failure):**
```json
{
  "servers": {
    "all": {
      "error": "URL Error",
      "message": "Connection refused"
    },
    "Node1": {
      "error": "URL Error",
      "message": "Connection refused"
    },
    "Node2": {
      "error": "URL Error",
      "message": "Connection refused"
    }
  },
  "deployments": {
    "error": "URL Error",
    "message": "Connection refused"
  },
  "timestamp": "2025-10-12T10:30:00.123456"
}
```

---

## WebLogic API Endpoints Called

The `/weblogic/info` endpoint makes the following calls to the WebLogic admin server:

1. **Get all servers:**
   ```
   GET {WEBLOGIC_ADMIN_URL}/management/tenant-monitoring/servers
   ```

2. **Get Node1 details:**
   ```
   GET {WEBLOGIC_ADMIN_URL}/management/tenant-monitoring/servers/Node1
   ```

3. **Get Node2 details:**
   ```
   GET {WEBLOGIC_ADMIN_URL}/management/tenant-monitoring/servers/Node2
   ```

4. **Get deployments and their state:**
   ```
   GET {WEBLOGIC_ADMIN_URL}/management/weblogic/latest/domainRuntime/appRuntimeStateRunning
   ```

All requests include:
- **Authorization:** Basic authentication with credentials `admin:admin`
- **Accept:** `application/json`
- **SSL Verification:** Disabled (for self-signed certificates)

---

## Usage Examples

### Starting the Server

```bash
# Configure WebLogic admin URL
export WEBLOGIC_ADMIN_URL="https://weblogic-admin.example.com:7001"

# Start the server
python3 server.py
```

### Testing Endpoints

```bash
# Get system info
curl http://localhost:8080/info | jq

# Get log tails
curl http://localhost:8080/logs/tail | jq

# Get WebLogic info
curl http://localhost:8080/weblogic/info | jq
```

### Using with a Remote Server

If the server is running on a remote machine:

```bash
# Replace 'remote-host' with your server hostname or IP
curl http://remote-host:8080/weblogic/info | jq
```

---

## Error Handling

All endpoints return appropriate HTTP status codes:

- **200 OK:** Request successful
- **404 Not Found:** Endpoint does not exist
- **500 Internal Server Error:** Server error with descriptive message

Error responses include a descriptive message:
```json
{
  "error": "Error description here"
}
```

---

## Notes

- The server uses only Python standard library (no external dependencies required)
- WebLogic SSL certificate verification is disabled to support self-signed certificates
- All WebLogic API calls have a 10-second timeout
- Each WebLogic endpoint failure is isolated - if one fails, others will still be attempted
- The server logs all incoming requests to stdout

