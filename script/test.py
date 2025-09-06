from http.server import HTTPServer, BaseHTTPRequestHandler
import json

class PingHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/ping':
            # Send response status code
            self.send_response(200)
            
            # Send headers
            self.send_header('Content-Type', 'text/plain')
            self.end_headers()
            
            # Send response body
            self.wfile.write(b'pong')
        else:
            # Handle 404 for other paths
            self.send_response(404)
            self.send_header('Content-Type', 'text/plain')
            self.end_headers()
            self.wfile.write(b'Not Found')
    
    def log_message(self, format, *args):
        # Custom log format
        print(f"[{self.address_string()}] {format % args}")

def run_server():
    server_address = ('', 3000)
    httpd = HTTPServer(server_address, PingHandler)
    print(f"Server running on http://localhost:3000")
    print("Send GET request to http://localhost:3000/ping to get 'pong'")
    print("Press Ctrl+C to stop the server")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")
        httpd.server_close()

if __name__ == '__main__':
    run_server()
