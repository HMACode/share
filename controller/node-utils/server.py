#!/usr/bin/env python3

import json
import subprocess
import os
import time
import ssl
import base64
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

LOGS_DIRECTORY = "/home/rtx/workspace/node-utils/mylogs"
WEBLOGIC_ADMIN_URL = os.environ.get('WEBLOGIC_ADMIN_URL', '')


class StatsHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        parsed_path = urlparse(self.path)
        
        if parsed_path.path == '/info':
            self.handle_info_request()
        elif parsed_path.path == '/logs/tail':
            self.handle_logs_tail_request()
        elif parsed_path.path == '/weblogic/info':
            self.handle_weblogic_info_request()
        else:
            self.send_error(404, "Endpoint not found")

    def handle_info_request(self):
        try:
            stats = self.collect_stats()
            self.send_json_response(stats)
        except Exception as e:
            self.send_error(500, f"Error collecting stats: {str(e)}")

    def handle_logs_tail_request(self):
        if not LOGS_DIRECTORY:
            self.send_error(500, "LOGS_DIRECTORY not configured")
            return
        
        if not os.path.isdir(LOGS_DIRECTORY):
            self.send_error(500, f"LOGS_DIRECTORY does not exist: {LOGS_DIRECTORY}")
            return
        
        try:
            log_tails = self.get_logs_tail()
            self.send_json_response(log_tails)
        except Exception as e:
            self.send_error(500, f"Error getting log tails: {str(e)}")

    def handle_weblogic_info_request(self):
        if not WEBLOGIC_ADMIN_URL:
            self.send_error(500, "WEBLOGIC_ADMIN_URL not configured")
            return
        
        try:
            weblogic_info = self.get_weblogic_info()
            self.send_json_response(weblogic_info)
        except Exception as e:
            self.send_error(500, f"Error getting WebLogic info: {str(e)}")

    def get_weblogic_info(self):
        result = {
            'servers': {},
            'deployments': {},
            'timestamp': datetime.now().isoformat()
        }
        
        ssl_context = ssl.create_default_context()
        ssl_context.check_hostname = False
        ssl_context.verify_mode = ssl.CERT_NONE
        
        credentials = base64.b64encode(b'admin:admin').decode('ascii')
        auth_header = f'Basic {credentials}'
        
        try:
            servers_url = f"{WEBLOGIC_ADMIN_URL}/management/tenant-monitoring/servers"
            servers_data = self.make_weblogic_request(servers_url, auth_header, ssl_context)
            result['servers']['all'] = servers_data
        except Exception as e:
            result['servers']['all'] = {'error': str(e)}
        
        try:
            node1_url = f"{WEBLOGIC_ADMIN_URL}/management/tenant-monitoring/servers/Node1"
            node1_data = self.make_weblogic_request(node1_url, auth_header, ssl_context)
            result['servers']['Node1'] = node1_data
        except Exception as e:
            result['servers']['Node1'] = {'error': str(e)}
        
        try:
            node2_url = f"{WEBLOGIC_ADMIN_URL}/management/tenant-monitoring/servers/Node2"
            node2_data = self.make_weblogic_request(node2_url, auth_header, ssl_context)
            result['servers']['Node2'] = node2_data
        except Exception as e:
            result['servers']['Node2'] = {'error': str(e)}
        
        try:
            deployments_url = f"{WEBLOGIC_ADMIN_URL}/management/weblogic/latest/domainRuntime/appRuntimeStateRunning"
            deployments_data = self.make_weblogic_request(deployments_url, auth_header, ssl_context)
            result['deployments'] = deployments_data
        except Exception as e:
            result['deployments'] = {'error': str(e)}
        
        return result

    def make_weblogic_request(self, url, auth_header, ssl_context):
        request = Request(url)
        request.add_header('Authorization', auth_header)
        request.add_header('Accept', 'application/json')
        
        try:
            with urlopen(request, context=ssl_context, timeout=10) as response:
                data = response.read().decode('utf-8')
                return json.loads(data)
        except HTTPError as e:
            return {
                'error': f'HTTP Error {e.code}',
                'message': e.reason
            }
        except URLError as e:
            return {
                'error': 'URL Error',
                'message': str(e.reason)
            }

    def get_logs_tail(self):
        try:
            today_midnight = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
            today_timestamp = today_midnight.timestamp()
            
            today_str = today_midnight.strftime('%Y-%m-%d')
            
            result = subprocess.run(
                ['find', LOGS_DIRECTORY, '-type', 'f', '-name', '*.log', '-newermt', today_str],
                capture_output=True,
                text=True,
                timeout=30
            )
            
            log_files = [f.strip() for f in result.stdout.strip().split('\n') if f.strip()]
            
            logs_data = []
            for log_file in log_files:
                try:
                    tail_result = subprocess.run(
                        ['tail', '-n', '20', log_file],
                        capture_output=True,
                        text=True,
                        timeout=5
                    )
                    
                    logs_data.append({
                        'file': log_file,
                        'tail': tail_result.stdout
                    })
                except Exception as e:
                    logs_data.append({
                        'file': log_file,
                        'error': f'Failed to read file: {str(e)}'
                    })
            
            return {
                'date': today_str,
                'files_count': len(logs_data),
                'logs': logs_data
            }
            
        except Exception as e:
            return {'error': f'Failed to get log tails: {str(e)}'}

    def collect_stats(self):
        stats = {}
        
        stats['memory'] = self.get_memory_stats()
        
        stats['disk'] = self.get_disk_stats()
        
        if LOGS_DIRECTORY and os.path.isdir(LOGS_DIRECTORY):
            stats['log_files'] = self.get_log_files_stats()
        
        return stats

    def get_memory_stats(self):
        mem_info = {}
        
        try:
            with open('/proc/meminfo', 'r') as f:
                lines = f.readlines()
            
            for line in lines:
                parts = line.split(':')
                if len(parts) == 2:
                    key = parts[0].strip()
                    value = parts[1].strip().split()[0]
                    mem_info[key] = int(value)
            
            total_ram_gb = mem_info.get('MemTotal', 0) / (1024 * 1024)
            available_ram_gb = mem_info.get('MemAvailable', 0) / (1024 * 1024)
            used_ram_gb = total_ram_gb - available_ram_gb
            
            swap_total_gb = mem_info.get('SwapTotal', 0) / (1024 * 1024)
            swap_free_gb = mem_info.get('SwapFree', 0) / (1024 * 1024)
            swap_used_gb = swap_total_gb - swap_free_gb
            
            return {
                'total_ram_gb': round(total_ram_gb, 2),
                'used_ram_gb': round(used_ram_gb, 2),
                'available_ram_gb': round(available_ram_gb, 2),
                'swap_used_gb': round(swap_used_gb, 2)
            }
        except Exception as e:
            return {'error': f'Failed to read memory info: {str(e)}'}

    def get_disk_stats(self):
        try:
            result = subprocess.run(
                ['df', '-h'],
                capture_output=True,
                text=True,
                check=True
            )
            
            lines = result.stdout.strip().split('\n')
            disk_info = []
            
            for line in lines[1:]:
                parts = line.split()
                if len(parts) >= 6:
                    use_percent_str = parts[4].rstrip('%')
                    try:
                        use_percent = int(use_percent_str)
                    except ValueError:
                        use_percent = 0
                    
                    disk_info.append({
                        'filesystem': parts[0],
                        'size': parts[1],
                        'used': parts[2],
                        'available': parts[3],
                        'use_percent': use_percent,
                        'mounted_on': parts[5]
                    })
            
            return disk_info
        except Exception as e:
            return {'error': f'Failed to get disk info: {str(e)}'}

    def get_log_files_stats(self):
        try:
            result = subprocess.run(
                ['find', LOGS_DIRECTORY, '-type', 'f', '-name', '*.log', '-exec', 'ls', '-lh', '{}', ';'],
                capture_output=True,
                text=True,
                timeout=10
            )
            
            files = []
            lines = result.stdout.strip().split('\n')
            
            for line in lines:
                if line:
                    parts = line.split()
                    if len(parts) >= 9:
                        size = parts[4]
                        filepath = ' '.join(parts[8:])
                        
                        try:
                            stat_result = os.stat(filepath)
                            size_bytes = stat_result.st_size
                            files.append({
                                'path': filepath,
                                'size': size,
                                'size_bytes': size_bytes
                            })
                        except:
                            pass
            
            files.sort(key=lambda x: x['size_bytes'], reverse=True)
            
            top_files = files[:3]
            
            for f in top_files:
                del f['size_bytes']
            
            return top_files
        except Exception as e:
            return {'error': f'Failed to get log files info: {str(e)}'}

    def format_size(self, size_bytes):
        for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
            if size_bytes < 1024.0:
                return f"{size_bytes:.2f} {unit}"
            size_bytes /= 1024.0
        return f"{size_bytes:.2f} PB"

    def send_json_response(self, data):
        json_data = json.dumps(data, indent=2)
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', len(json_data))
        self.end_headers()
        self.wfile.write(json_data.encode('utf-8'))

    def log_message(self, format, *args):
        print(f"{self.address_string()} - [{self.log_date_time_string()}] {format % args}")


def main():
    server_address = ('', 8080)
    httpd = HTTPServer(server_address, StatsHandler)
    
    print(f"Server running on port 8080...")
    print(f"Logs directory: {LOGS_DIRECTORY if LOGS_DIRECTORY else 'Not configured'}")
    print(f"WebLogic admin URL: {WEBLOGIC_ADMIN_URL if WEBLOGIC_ADMIN_URL else 'Not configured'}")
    print(f"Available endpoints:")
    print(f"  - http://localhost:8080/info")
    print(f"  - http://localhost:8080/logs/tail")
    print(f"  - http://localhost:8080/weblogic/info")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server...")
        httpd.shutdown()


if __name__ == '__main__':
    main()
