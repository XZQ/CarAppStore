#!/usr/bin/env python3
"""Serve ignored LOCAL_SIM APK assets with HTTP Range support."""

import argparse
import http.server
import os
import re
import socketserver


class RangeHandler(http.server.SimpleHTTPRequestHandler):
    """Static handler that serves byte ranges required by RealFileDownloader."""

    protocol_version = "HTTP/1.1"

    def __init__(self, *args, directory: str, **kwargs):
        super().__init__(*args, directory=directory, **kwargs)

    def end_headers(self):
        self.send_header("Accept-Ranges", "bytes")
        super().end_headers()

    def do_GET(self):
        range_header = self.headers.get("Range")
        match = re.match(r"bytes=(\d+)-(\d*)$", range_header or "")
        if match is None:
            return super().do_GET()
        path = self.translate_path(self.path)
        try:
            file_size = os.path.getsize(path)
            start = int(match.group(1))
            end = min(int(match.group(2)) if match.group(2) else file_size - 1, file_size - 1)
            if start >= file_size or end < start:
                self.send_error(416, "Requested Range Not Satisfiable")
                return
            with open(path, "rb") as file:
                file.seek(start)
                payload = file.read(end - start + 1)
        except OSError:
            return super().do_GET()
        self.send_response(206)
        self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Content-Type", self.guess_type(path))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format, *args):
        return


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset-dir", default="local-apks", help="Ignored directory holding catalog.json and APK files")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()
    asset_dir = os.path.abspath(args.asset_dir)
    os.makedirs(asset_dir, exist_ok=True)
    handler = lambda *handler_args, **handler_kwargs: RangeHandler(*handler_args, directory=asset_dir, **handler_kwargs)
    socketserver.ThreadingTCPServer.daemon_threads = True
    with socketserver.ThreadingTCPServer(("0.0.0.0", args.port), handler) as server:
        print(f"Serving {asset_dir} on http://0.0.0.0:{args.port}")
        print(f"Emulator access: http://10.0.2.2:{args.port}")
        server.serve_forever()


if __name__ == "__main__":
    main()
