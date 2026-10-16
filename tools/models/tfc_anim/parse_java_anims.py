"""Parse TFC/vanilla Java `AnimationDefinition` constants (Blockbench "Animation to Java" export style).

Returns {CONST_NAME: {"length": float, "loop": bool, "bones": {bone: {"rotation"|"position"|"scale": [(t, (x,y,z), interp)]}}}}
Values are exactly as written in Java (degreeVec/posVec/scaleVec arguments), with no axis conversion here.
"""
import re
import sys

ANIM_RE = re.compile(r"public static final AnimationDefinition (\w+)\s*=\s*AnimationDefinition\.Builder\.withLength\(([\d.]+)f?F?\)(.*?)\.build\(\);", re.S)
ADD_RE = re.compile(r'\.addAnimation\("(\w+)",\s*new AnimationChannel\(AnimationChannel\.Targets\.(\w+),(.*?)\)\)(?=\s*(?:\.addAnimation|$))', re.S)
KF_RE = re.compile(r"new Keyframe\(\s*([-\d.]+)[fF]?,\s*KeyframeAnimations\.(\w+)\(\s*([-\d.]+)[fF]?,\s*([-\d.]+)[fF]?,\s*([-\d.]+)[fF]?\s*\),\s*AnimationChannel\.Interpolations\.(\w+)\)")
TARGET = {"ROTATION": "rotation", "POSITION": "position", "SCALE": "scale"}


def parse(java_source: str) -> dict:
    out = {}
    for name, length, body in ANIM_RE.findall(java_source):
        anim = {"length": float(length), "loop": ".looping()" in body, "bones": {}}
        for bone, target, chan in ADD_RE.findall(body):
            kfs = [(float(t), (float(x), float(y), float(z)), interp.lower()) for t, _fn, x, y, z, interp in KF_RE.findall(chan)]
            anim["bones"].setdefault(bone, {}).setdefault(TARGET[target], []).extend(kfs)
        out[name] = anim
    return out


if __name__ == "__main__":
    import json
    for path in sys.argv[1:]:
        print(path)
        print(json.dumps({k: {"length": v["length"], "loop": v["loop"], "bones": sorted(v["bones"])} for k, v in parse(open(path).read()).items()}, indent=1))
