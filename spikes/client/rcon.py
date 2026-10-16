#!/usr/bin/env python3
"""Minimal RCON client: rcon.py <port> <command...>  (password "spike", see server.sh)."""
import socket, struct, sys

def packet(i, t, body):
    b = body.encode() + b"\x00\x00"
    return struct.pack("<iii", len(b) + 8, i, t) + b

def recv(s):
    n = struct.unpack("<i", s.recv(4))[0]
    data = b""
    while len(data) < n:
        data += s.recv(n - len(data))
    return data[8:-2].decode(errors="replace")

def run(port, cmd, password="spike"):
    with socket.create_connection(("127.0.0.1", port), timeout=30) as s:
        s.sendall(packet(1, 3, password)); recv(s)
        s.sendall(packet(2, 2, cmd)); return recv(s)

if __name__ == "__main__":
    print(run(int(sys.argv[1]), " ".join(sys.argv[2:])))
