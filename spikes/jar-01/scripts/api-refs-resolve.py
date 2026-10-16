import re, subprocess, sys, json, functools
JAVAP = "/opt/jdk/25/bin/javap"
cp = sys.argv[1]
refs = json.load(open(sys.argv[2]))

def strip_generics(s):
    prev = None
    while prev != s:
        prev = s
        s = re.sub(r"<[^<>]*>", "", s)
    return s.strip()

@functools.lru_cache(None)
def members(owner):
    out = subprocess.run([JAVAP, "-s", "-cp", cp, owner], capture_output=True, text=True)
    if out.returncode != 0:
        return None
    lines = out.stdout.splitlines()
    simple = owner.split(".")[-1].split("$")[-1]
    found = set()
    for i, line in enumerate(lines):
        if line.startswith("  ") and "(" in line and i + 1 < len(lines) and lines[i+1].strip().startswith("descriptor:"):
            head = line.strip().split("(")[0].split()[-1]
            if head == simple:
                name = "<init>"
            else:
                name = head
            desc = lines[i+1].strip().split("descriptor: ")[1]
            found.add((name, desc))
    # supertypes from the declaration header (may span lines until '{')
    decl = subprocess.run([JAVAP, "-cp", cp, owner], capture_output=True, text=True).stdout
    header = decl.split("{")[0]
    header = header.split("\n", 1)[1] if header.startswith("Compiled") or "\n" in header else header
    header = " ".join(l for l in header.splitlines() if not l.startswith("Compiled") and not l.startswith("Picked"))
    supers = []
    m = re.search(r"\b(?:extends|implements)\s+(.*)$", strip_generics(header))
    # header after stripping generics: e.g. "public interface X extends A,B"
    flat = strip_generics(header)
    ext = re.search(r"\bextends\s+(.*?)(?=\bimplements\b|$)", flat)
    imp = re.search(r"\bimplements\s+(.*)$", flat)
    if ext: supers += [s.strip() for s in ext.group(1).split(",") if s.strip()]
    if imp: supers += [s.strip() for s in imp.group(1).split(",") if s.strip()]
    if not supers and not owner == "java.lang.Object":
        supers = ["java.lang.Object"]
    for s in supers:
        s = s.strip()
        if s and s != owner:
            sub = members(s)
            if sub:
                found |= sub
    return found

missing = []
for owner, name, desc in refs:
    ms = members(owner)
    if ms is None:
        missing.append((owner, name, desc, "CLASS NOT FOUND")); continue
    if (name, desc) not in ms:
        missing.append((owner, name, desc, "method not found"))
print(f"checked {len(refs)} refs: {len(missing)} missing")
for m in missing:
    print("  MISSING", m)
