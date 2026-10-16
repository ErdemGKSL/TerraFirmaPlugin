# Licensed under the EUPL, Version 1.2.
"""Procedural animation library keyed to the standard bone names in rigs.py.

All animations use BetterModel names (idle, walk, run, eat, ...). Values are in Blockbench 5 conventions
(FORMAT_VERSION in bbmodel.py). Species params may scale amplitude/period via p["anim"].
"""
from __future__ import annotations

from .bbmodel import Animation, Keyframe


def _swing(bone, length, amp, phase=0.0, steps=4):
    """Sampled sinusoidal x-rotation, loop-closed (first == last keyframe)."""
    import math
    kfs = []
    for i in range(steps + 1):
        t = length * i / steps
        kfs.append(Keyframe(t, "rotation", (round(amp * math.sin(2 * math.pi * (i / steps + phase)), 3), 0, 0), "catmullrom"))
    return {bone: kfs}


def quadruped_walk(p: dict) -> Animation:
    a = p.get("anim", {})
    length, amp = a.get("walk_period", 1.0), a.get("walk_amp", 25.0)
    tracks = {}
    for bone, phase in (("leg_fl", 0.0), ("leg_br", 0.0), ("leg_fr", 0.5), ("leg_bl", 0.5)):   # diagonal gait
        tracks.update(_swing(bone, length, amp, phase))
    tracks.update(_swing("h_head", length, amp * 0.12, 0.25))
    if "tail" in p:
        tracks.update(_swing("tail", length, amp * 0.3, 0.1))
    return Animation("walk", length, tracks=tracks)


def quadruped_idle(p: dict) -> Animation:
    tracks = _swing("h_head", 4.0, 3.0, 0.0)
    if "tail" in p:
        tracks.update(_swing("tail", 2.0, 8.0, 0.0))
    return Animation("idle", 4.0, tracks=tracks)


def quadruped_eat(p: dict) -> Animation:
    """Head down to graze; override=True so legs keep walking/idling underneath.
    Blockbench 5 convention (verified by render): -x tilts an upward part forward (-z) and pitches a forward-facing part down."""
    k = lambda t, x: Keyframe(t, "rotation", (x, 0, 0), "catmullrom")
    return Animation("eat", 2.0, loop="once", override=True,
                     tracks={"neck": [k(0, 0), k(0.4, -100), k(1.6, -100), k(2.0, 0)], "h_head": [k(0, 0), k(0.4, 40), k(1.0, 30), k(1.6, 40), k(2.0, 0)]})


LIBRARY = {"quadruped": [quadruped_idle, quadruped_walk, quadruped_eat]}
