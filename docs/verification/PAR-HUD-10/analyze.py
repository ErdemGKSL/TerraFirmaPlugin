"""Frame timeline of a measure.sh recording: per frame, the title's red pixels and the popup band's colours.
Usage: python3 -I analyze.py <dir> <band_y0> <band_y1>
Prints each RCON reply and every frame where the classification changes, plus the latency per reply."""
import subprocess, sys, json
D, y0, y1 = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
W, H = 1280, 720
probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
                        "-of", "json", D + "/rec.mkv"], capture_output=True, text=True, check=True)
times = [float(f["pts_time"]) for f in json.loads(probe.stdout)["frames"]]
raw = subprocess.Popen(["ffmpeg", "-v", "error", "-copyts", "-i", D + "/rec.mkv", "-vsync", "passthrough",
                        "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
def count(buf, ya, yb, xa, xb, rgb, tol=12):
    n = 0
    for y in range(ya, yb):
        row = y * W * 3
        for x in range(xa, xb, 1):
            i = row + x * 3
            if abs(buf[i] - rgb[0]) <= tol and abs(buf[i + 1] - rgb[1]) <= tol and abs(buf[i + 2] - rgb[2]) <= tol:
                n += 1
    return n
events = []
for line in open(D + "/rcon.log"):
    ts, rest = line.split(" ", 1)
    if rest.startswith("reply:"):
        events.append((float(ts), rest.split("|")[0].strip()))
frames = []
idx = 0
while True:
    buf = raw.stdout.read(W * H * 3)
    if len(buf) < W * H * 3:
        break
    t = times[idx] if idx < len(times) else None
    idx += 1
    title = count(buf, 240, 330, 520, 760, (0xff, 0x55, 0x55))
    red = count(buf, y0, y1, 440, 840, (0xff, 0x55, 0x55))
    dark = count(buf, y0, y1, 440, 840, (0xaa, 0x00, 0x00))
    state = ("TITLE " if title > 50 else "") + ("RED" if red > 30 else "DARK_RED" if dark > 30 else "none")
    frames.append((t, state, title, red, dark))
prev = None
timeline = [(t, "frame", s, a, b, c) for t, s, a, b, c in frames] + [(t, "reply", e) for t, e in events]
timeline.sort(key=lambda x: x[0])
for item in timeline:
    if item[1] == "reply":
        print("%.3f REPLY %s" % (item[0], item[2]))
    elif item[2] != prev:
        print("%.3f frame: %s (title %d, red %d, dark %d)" % (item[0], item[2], item[3], item[4], item[5]))
        prev = item[2]
# latency: first frame after each reply whose state differs from the frame state at the reply
print("--- latency per reply (first changed frame)")
for t, e in events:
    before = [f for f in frames if f[0] <= t]
    after = [f for f in frames if f[0] > t]
    if not before or not after:
        continue
    s0 = before[-1][1]
    ch = next((f for f in after if f[1] != s0), None)
    gaps = [b[0] - a[0] for a, b in zip(after, after[1:11])]
    print("%-50s state %-10s -> %s after %s s (frame gap ~%.3f s)" % (e[:50], s0, ch[1] if ch else "-",
          "%.3f" % (ch[0] - t) if ch else "-", sum(gaps) / max(1, len(gaps))))
