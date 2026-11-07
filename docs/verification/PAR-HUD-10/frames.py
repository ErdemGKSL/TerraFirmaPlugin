"""Saves, for each heat reply in rcon.log, the first recorded frame at least <delay> s after it (full screen PNG and a
3x crop of the popup band). Usage: python3 -I frames.py <dir> <prefix> <delay> <crop_y0> <crop_y1>"""
import subprocess, sys, json
D, prefix, delay, y0, y1 = sys.argv[1], sys.argv[2], float(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
                        "-of", "json", D + "/rec.mkv"], capture_output=True, text=True, check=True)
times = [float(f["pts_time"]) for f in json.loads(probe.stdout)["frames"]]
seen = {}
for line in open(D + "/rcon.log"):
    ts, rest = line.split(" ", 1)
    if rest.startswith("reply: tfp debug heat Bot "):
        t = rest.split("|")[0].split()[-1]
        n = seen.get(t, 0) + 1
        seen[t] = n
        if n > 1:
            continue
        r = float(ts)
        idx = next(i for i, ft in enumerate(times) if ft >= r + delay)
        name = "%s-%s" % (prefix, t)
        full = "%s/%s-full.png" % (D, name)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", D + "/rec.mkv", "-vf", "select=eq(n\\,%d)" % idx,
                        "-vsync", "0", "-frames:v", "1", full], check=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", full, "-vf",
                        "crop=480:%d:400:%d,scale=iw*3:ih*3:flags=neighbor" % (y1 - y0, y0),
                        "%s/%s-crop-3x.png" % (D, name)], check=True)
        print("%s: frame %d at +%.3f s after the reply" % (name, idx, times[idx] - r))
