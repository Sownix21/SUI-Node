"""Loopback-only, in-memory APIv2 fixture for Android emulator smoke tests.

Run with Python 3; add http://10.0.2.2:18995/app/ and token fixture-token.
This is NOT a panel/sing-box emulator and never contacts a real server.
"""
import copy
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit


INBOUNDS = [
    {"id": 3, "type": "vless", "tag": "vless-fixture", "listen": "::", "listen_port": 1443,
     "tls_id": 0, "addrs": [], "out_json": {}, "users": ["alice"], "future_option": "preserve-me"},
    {"id": 4, "type": "hysteria2", "tag": "hy2-fixture", "listen": "::", "listen_port": 2443,
     "tls_id": 1, "addrs": [], "out_json": {}, "users": ["alice"], "up_mbps": 100, "down_mbps": 200},
]
CLIENTS = [{"id": 7, "name": "alice", "enable": True, "inbounds": [3, 4], "group": "QA",
            "up": 1048576, "down": 4194304, "volume": 0, "expiry": 0, "desc": "Fixture client",
            "config": {"vless": {"uuid": "1bc8b976-ea04-47bc-9fd7-e47c2a364b26"}, "hysteria2": {"password": "fixture-password"}},
            "links": [], "autoReset": False, "resetDays": 30}]
STATE = {"clients": CLIENTS, "inbounds": INBOUNDS, "outbounds": [{"id": 1, "type": "direct", "tag": "direct"}],
         "endpoints": [], "services": [], "tls": [{"id": 1, "name": "fixture-tls", "enabled": True}],
         "config": {"dns": {"servers": []}, "route": {"rules": [], "rule_set": []}, "http_clients": []},
         "enableTraffic": True, "os": "linux", "subURI": "http://fixture.invalid/sub/",
         "onlines": {"user": ["alice"], "inbound": ["vless-fixture"], "outbound": []}}


def summary(collection):
    keys = {"clients": ("id", "name", "enable", "inbounds", "group", "up", "down", "volume", "expiry", "desc"),
            "inbounds": ("id", "type", "tag", "listen", "listen_port", "tls_id", "users")}
    return [{k: v for k, v in row.items() if k in keys[collection]} for row in STATE[collection]]


class Handler(BaseHTTPRequestHandler):
    # Match the Go panel's HTTP/1.1 keep-alive behavior. Python's HTTP/1.0 default
    # closes without a Connection header and is unsuitable for pooled clients.
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass

    def reply(self, obj=None, success=True, msg=""):
        body = json.dumps({"success": success, "msg": msg, "obj": obj}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def route(self):
        parsed = urlsplit(self.path)
        if not parsed.path.startswith("/app/apiv2/"):
            self.reply(success=False, msg="Only APIv2 is available")
            return None, None
        if self.headers.get("Token") != "fixture-token":
            self.reply(success=False, msg="invalid token")
            return None, None
        return parsed.path.rsplit("/", 1)[-1], parse_qs(parsed.query)

    def do_GET(self):
        action, query = self.route()
        if action is None:
            return
        if action != "status" and action != "load":
            print("GET", action, query, flush=True)
        if action == "load":
            self.reply(copy.deepcopy(STATE))
        elif action in STATE:
            value = STATE[action]
            if action in ("clients", "inbounds"):
                value = ([row for row in value if str(row["id"]) == query["id"][0]]
                         if "id" in query else summary(action))
            self.reply({action: value})
        elif action == "status":
            self.reply({"cpu": 23.5, "mem": {"current": 1073741824, "total": 4294967296},
                        "dsk": {"current": 10737418240, "total": 42949672960},
                        "swp": {"current": 0, "total": 1073741824},
                        "net": {"sent": 10000, "recv": 20000, "psent": 100, "precv": 200},
                        "dio": {"read": 10000, "write": 20000},
                        "sbd": {"running": True, "stats": {"Uptime": 3600, "Alloc": 10485760, "NumGoroutine": 20}},
                        "sys": {"hostName": "fixture-server", "os": "linux", "platform": "ubuntu"},
                        "db": {"clients": 1, "inbounds": 2}})
        elif action in ("users", "changes", "logs"):
            self.reply([])
        elif action == "stats":
            self.reply({"stats": {"0": [1024, 4096]}, "numBuckets": 24, "bucketSpan": 3600, "startTime": 0})
        else:
            self.reply(success=False, msg="Not implemented in this test fixture")

    def do_POST(self):
        action, _ = self.route()
        if action is None:
            return
        form = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode())
        if action != "save":
            self.reply(success=False, msg="Fixture only permits save")
            return
        try:
            collection, operation = form["object"][0], form["action"][0]
            record = json.loads(form["data"][0])
            if collection == "config":
                STATE[collection] = record
            elif operation in ("edit", "new"):
                if operation == "new":
                    record["id"] = max((row["id"] for row in STATE[collection]), default=0) + 1
                    STATE[collection].append(record)
                else:
                    index = next(i for i, row in enumerate(STATE[collection]) if row["id"] == record["id"])
                    STATE[collection][index] = record
            else:
                raise ValueError("Only new/edit are implemented in fixture")
            print("SAVE", collection, operation, json.dumps(record), flush=True)
            self.reply({collection: STATE[collection]}, msg="saved")
        except (ValueError, KeyError, StopIteration) as error:
            self.reply(success=False, msg=str(error))


if __name__ == "__main__":
    print("APIv2 fixture: 127.0.0.1:18995 (emulator 10.0.2.2:18995), token fixture-token", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 18995), Handler).serve_forever()
