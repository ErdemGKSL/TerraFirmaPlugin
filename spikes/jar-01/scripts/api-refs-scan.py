import re, subprocess, sys, glob, json
JAVAP = "/opt/jdk/25/bin/javap"
classes = glob.glob(sys.argv[1] + "/dev/tfp/spike/jar01/*.class")
refs = set()
for c in classes:
    out = subprocess.run([JAVAP, "-v", "-p", c], capture_output=True, text=True).stdout
    for line in out.splitlines():
        m = re.search(r"= (Methodref|InterfaceMethodref)\s+#\d+\.#\d+\s+// (\S+)\.(\S+?):(\(.*\).*)$", line)
        if m:
            owner, name, desc = m.group(2), m.group(3), m.group(4)
            if owner.startswith(("net/kyori", "io/papermc", "org/bukkit", "com/github", "org/eclipse")):
                refs.add((owner.replace("/", "."), name, desc))
print(len(refs), "unique API method refs")
open(sys.argv[2], "w").write(json.dumps(sorted(refs)))
