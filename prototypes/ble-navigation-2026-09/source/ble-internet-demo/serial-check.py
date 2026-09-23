"""Read demo diagnostics without writing commands or resetting the ESP32."""
import argparse
import time
import serial

parser = argparse.ArgumentParser()
parser.add_argument('--port', required=True)
parser.add_argument('--seconds', type=int, default=16)
args = parser.parse_args()
with serial.Serial(port=None, baudrate=115200, timeout=0.5) as connection:
    connection.dtr = False
    connection.rts = False
    connection.port = args.port
    connection.open()
    deadline = time.monotonic() + args.seconds
    while time.monotonic() < deadline:
        line = connection.readline()
        if line:
            print(line.decode('utf-8', errors='replace').rstrip(), flush=True)
