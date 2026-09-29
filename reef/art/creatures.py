"""
The 14 faction portraits, 120 x 120, as linocut prints: dark ink, one accent color per faction
and lines carved into the shapes, like the plates of an old naturalist's field guide.
"""
import math

from artlib import (Art, blob, taper, circle, ellipse, poly, smooth, lin, rad, vgrad, lighten, darken, mix,
                    arc_pt, ring_points, spline_points, along, lerp, f, pt)
from lino import INK, CREAM, INKS, carve, hatch, dots, lino_eye

W = 120


def eye(a, x, y, r, iris=None, ink="#10161c"):
    """A small eye for the pictures in pieces.py (they are pressed onto the print palette later)."""
    if iris:
        a.add(circle(x, y, r), fill=iris, stroke=ink, width=r * 0.35)
        a.add(circle(x + r * 0.08, y, r * 0.62), fill=ink)
    else:
        a.add(circle(x, y, r), fill=ink)
    a.add(circle(x + r * 0.3, y - r * 0.32, r * 0.3), fill="#ffffff")


def shine(a, points, w0=3.0, w1=1.0, alpha=0.45):
    a.add(taper(points, w0, w1), fill="#ffffff", alpha=alpha)


def outlined(a, shapes, fill, ink, width):
    """Several overlapping shapes that read as one: outline pass first, then fill pass on top."""
    for d in shapes:
        a.add(d, fill=ink, stroke=ink, width=width)
    for d in shapes:
        a.add(d, fill=fill)


def rays(pts_from, pts_to):
    return [f"M{pt(p)} L{pt(q)}" for p, q in zip(pts_from, pts_to)]


# ---- Predators and swarms -------------------------------------------------------------------------


def shark():
    a = Art("shark", W, W, "creature")
    accent = "#3f8fa3"
    tail = blob([(36, 56), (24, 45), (14, 33), (9, 25), (13, 36), (18, 48), (21, 60), (18, 72), (13, 84), (9, 92), (19, 83), (28, 73), (36, 67)])
    fin = blob([(52, 45), (58, 30), (64, 17), (70, 21), (74, 34), (82, 46)])
    pec = blob([(70, 72), (62, 86), (54, 97), (64, 94), (76, 84), (84, 74)])
    for d in (tail, fin):
        a.add(d, fill=INK)
    carve(a, fin, [smooth([(58, 42), (63, 28), (66, 20)], closed=False)], 1.6)
    carve(a, tail, [smooth([(30, 58), (20, 46), (13, 32)], closed=False), smooth([(30, 64), (20, 74), (13, 86)], closed=False)], 1.4)
    body = blob([(111, 61), (102, 50), (86, 43), (68, 40), (50, 43), (36, 50), (26, 58), (27, 65), (38, 72), (54, 78), (72, 80), (90, 77), (103, 69)])
    a.add(body, fill=accent, stroke=INK, width=4)
    belly = blob([(106, 66), (92, 74), (72, 78), (54, 76), (40, 70), (54, 71), (72, 72), (90, 70)])
    a.add(belly, fill=CREAM)
    carve(a, body, [smooth([(30, 56 + k * 3.2), (54, 46 + k * 3.2), (84, 46 + k * 3.2), (108, 58 + k * 2.4)], closed=False) for k in range(4)], 1.4, INK)
    carve(a, belly, [f"M{x},64 L{x - 4},80" for x in range(44, 108, 6)], 1.2, accent)
    a.add(pec, fill=INK)
    carve(a, pec, [smooth([(72, 76), (64, 86), (58, 94)], closed=False)], 1.4)
    for x in (82, 87, 92):
        a.line(f"M{x},52 Q{x - 2},59 {x},66", INK, 2.2)
    lino_eye(a, 99, 55, 4.2)
    a.line("M106,66 Q99,69 91,68", INK, 2)
    return a


def sardines():
    a = Art("sardines", W, W, "creature")
    accent = "#6f9cc4"

    def fish(tx, ty, s):
        with a.group(tx=tx, ty=ty, sx=s):
            a.add(blob([(-24, 0), (-32, -7), (-38, -11), (-35, -3), (-34, 0), (-35, 3), (-38, 11), (-32, 7)]), fill=INK)
            body = blob([(31, 0), (21, -7.5), (4, -9.5), (-12, -7.5), (-25, -3), (-29, 0), (-25, 3), (-12, 7.5), (4, 9.5), (21, 7.5)])
            a.add(body, fill=accent, stroke=INK, width=2.6)
            with a.group(clip=body):
                a.add("M-40,-20 H40 V-3 Q0,-5 -40,-2 Z", fill=INK)
                a.add("M-40,20 H40 V3.5 Q0,5 -40,2 Z", fill=CREAM)
                for x in range(-18, 20, 5):
                    a.line(f"M{x},-3 Q{x + 2.5},0 {x},3", CREAM, 1.1)
                for x in range(-16, 24, 4):
                    a.line(f"M{x},-8 L{x + 2},-4", CREAM, 0.9, alpha=0.7)
            lino_eye(a, 22, -1.2, 2.8)

    fish(58, 34, 1.05)
    fish(42, 62, 1.15)
    fish(70, 88, 1.0)
    for x, y, r in ((100, 22, 2.6), (106, 33, 1.8), (18, 40, 2)):
        a.add(circle(x, y, r), fill=CREAM, stroke=INK, width=1.3)
    return a


def lionfish():
    a = Art("lionfish", W, W, "creature")
    accent = "#d9642e"
    # Fan fins below: cream with printed rays.
    root = (58, 71)
    tips = [(56, 112), (43, 109), (31, 101), (22, 91), (17, 80)]
    fan = blob([root] + [lerp(root, t, 0.95) for t in tips])
    a.add(fan, fill=CREAM, stroke=INK, width=2.6)
    carve(a, fan, rays([root] * 5, tips), 2, INK)
    # Spines and the membrane between them.
    bases = [(44, 53), (51, 50), (58, 48.5), (65, 48), (72, 48), (79, 49), (86, 51)]
    tops = [(30, 26), (38, 14), (50, 7), (63, 5), (76, 8), (88, 15), (97, 26)]
    membrane = smooth(bases + [lerp(b, t, 0.66) for b, t in zip(bases[::-1], tops[::-1])])
    a.add(membrane, fill=accent, stroke=INK, width=2)
    for b, t in zip(bases, tops):
        a.add(taper([b, t], 3.6, 1.2), fill=INK)
    tail = blob([(34, 62), (22, 50), (11, 46), (8, 63), (11, 80), (22, 76), (34, 68)])
    a.add(tail, fill=CREAM, stroke=INK, width=2.6)
    carve(a, tail, rays([(32, 64)] * 5, [(8, 48), (6, 56), (6, 64), (6, 72), (8, 80)]), 1.8, INK)
    body = blob([(101, 62), (94, 52), (80, 47), (62, 47), (46, 51), (35, 58), (31, 64), (37, 70), (50, 76), (66, 79.5), (82, 77.5), (95, 71)])
    a.add(body, fill=CREAM)
    with a.group(clip=body):
        for x in (40, 50, 60, 70, 80):
            a.add(taper([(x + 3, 44), (x - 1, 64), (x + 2, 84)], 5.6, 4.6), fill=accent)
        a.add("M20,70 Q60,86 110,68 V100 H20 Z", fill=INK, alpha=0.18)
    a.add(body, stroke=INK, width=4)
    a.add(taper([(89, 54), (88, 45), (84, 38)], 2.8, 1.2), fill=INK)
    lino_eye(a, 91, 59, 4)
    a.line("M102,64 Q97,66 92,65.5", INK, 2)
    return a


def starfish():
    a = Art("starfish", W, W, "creature")
    accent = "#d4577f"
    cx, cy = 60, 63
    arm = taper([(cx, cy), (cx, cy - 26), (cx, cy - 50)], 27, 10, ease=0.9)
    angles = [0, 72, 144, 216, 288]
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            a.add(arm, fill=INK, stroke=INK, width=7)
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            a.add(arm, fill=accent)
    a.add(circle(cx, cy, 16), fill=accent)
    # Shade the far side with printed hatching.
    for ang in (72, 144):
        with a.group(rot=ang, px=cx, py=cy):
            with a.group(clip=arm):
                for k in range(-2, 8):
                    x = cx + 3 + k * 3.2
                    a.line(f"M{f(x)},{cy - 60} L{f(x)},{cy}", INK, 1.2)
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            dots(a, [(cx, cy - 18), (cx, cy - 27), (cx, cy - 36), (cx - 6, cy - 22), (cx + 6, cy - 22), (cx, cy - 44)], 2.2)
    a.add(circle(cx, cy, 6), fill=CREAM, stroke=INK, width=2)
    return a


# ---- Builders and drifters --------------------------------------------------------------------------


def coral():
    a = Art("coral", W, W, "creature")
    accent = "#e0606a"
    rock = blob([(18, 112), (32, 100), (60, 97), (88, 100), (104, 112), (60, 117)])
    a.add(rock, fill=INK)
    carve(a, rock, [f"M{x},104 Q{x + 10},101 {x + 20},104" for x in (26, 50, 74)], 1.4)
    branches = [
        ([(60, 108), (59, 94), (57, 80), (56, 68)], 14, 11),
        ([(57, 80), (48, 70), (42, 58), (40, 46)], 10, 7),
        ([(41, 53), (34, 46), (30, 38), (28, 27)], 7, 5.5),
        ([(40, 49), (44, 39), (45, 29), (43, 20)], 7, 5.5),
        ([(56, 70), (62, 58), (65, 44), (66, 29)], 10, 7),
        ([(65, 44), (72, 37), (79, 30), (85, 22)], 7, 5.5),
        ([(59, 92), (70, 85), (80, 76), (86, 62)], 10, 7),
        ([(84, 68), (90, 60), (94, 52), (97, 41)], 7, 5),
        ([(58, 94), (46, 87), (36, 80), (26, 70)], 9, 6),
        ([(33, 78), (25, 73), (19, 66), (15, 56)], 6.5, 4.5),
    ]
    shapes = [taper(p, w0, w1) for p, w0, w1 in branches]
    outlined(a, shapes, accent, INK, 5)
    for p, w0, w1 in branches:
        a.add(taper([(x + w0 * 0.12, y) for x, y in p[1:]], max(1.4, w1 * 0.28), 1.0), fill=CREAM)
    for p, _, _ in branches:
        x, y = p[-1]
        a.add(circle(x, y, 2.4), fill=CREAM, stroke=INK, width=1.2)
    return a


def jellyfish():
    a = Art("jellyfish", W, W, "creature")
    accent = "#9a6cc8"
    for x in range(28, 95, 11):
        a.add(taper([(x, 60), (x - 4, 74), (x + 3, 88), (x - 3, 102), (x + 1, 116)], 2.6, 1), fill=INK)
    for pts, w in (([(52, 60), (47, 76), (55, 90), (48, 106)], 9), ([(68, 60), (73, 78), (65, 92), (71, 108)], 9)):
        arm = taper(pts, w, 3)
        a.add(arm, fill=accent, stroke=INK, width=2.4)
        carve(a, arm, [smooth([(x - 0.5, y) for x, y in pts], closed=False)], 1.4)
    dome = ring_points(60, 58, 35, 40, 180, 360, 11)
    rim = [(x, 61 if i % 2 == 0 else 65.5) for i, x in enumerate(range(95, 24, -7))]
    bell = blob(dome + rim)
    a.add(bell, fill=accent)
    carve(a, bell, [f"M60,14 L{f(60 + 60 * math.cos(math.radians(d)))},{f(14 + 60 * math.sin(math.radians(d)))}" for d in range(20, 170, 12)], 1.2, INK)
    for x, y in ((47, 45), (60, 40), (73, 45)):
        a.add(ellipse(x, y, 5, 6.6), fill=CREAM, stroke=INK, width=2)
    carve(a, bell, [smooth([(32, 50), (36, 34), (48, 24)], closed=False)], 2.6)
    a.add(bell, stroke=INK, width=4)
    return a


def parrotfish():
    a = Art("parrotfish", W, W, "creature")
    accent = "#2aa594"
    tail = blob([(28, 55), (17, 43), (8, 36), (11, 50), (13, 60), (11, 70), (8, 84), (17, 77), (28, 66)])
    a.add(tail, fill=INK)
    carve(a, tail, rays([(26, 60)] * 4, [(10, 40), (9, 54), (9, 68), (10, 82)]), 1.4)
    dorsal = blob([(40, 41), (50, 30), (66, 26), (82, 29), (93, 40), (80, 36), (64, 34), (50, 37)])
    a.add(dorsal, fill=INK)
    body = blob([(105, 60), (100, 48), (88, 38), (70, 33), (52, 34), (38, 40), (28, 50), (25, 58), (28, 66), (38, 76), (54, 83), (72, 85), (90, 80), (101, 71)])
    a.add(body, fill=accent)
    with a.group(clip=body):
        for row, y in enumerate(range(40, 86, 8)):
            for col in range(9):
                x = 30 + col * 9 + (4.5 if row % 2 else 0)
                a.line(f"M{f(x - 4)},{y} Q{f(x)},{y + 5} {f(x + 4)},{y}", CREAM, 1.4)
        a.add("M20,72 Q60,86 110,70 V100 H20 Z", fill=INK, alpha=0.25)
    a.add(body, stroke=INK, width=4)
    pec = blob([(72, 62), (63, 70), (58, 79), (67, 77), (78, 66)])
    a.add(pec, fill=CREAM, stroke=INK, width=2.4)
    beak = blob([(100, 54), (107, 57), (109, 63), (104, 67), (99, 63)])
    a.add(beak, fill=CREAM, stroke=INK, width=2.4)
    a.line("M100,60.5 L108.5,60.5", INK, 1.6)
    lino_eye(a, 90, 53, 4.4)
    return a


# ---- Wanderers ----------------------------------------------------------------------------------------


def turtle():
    a = Art("turtle", W, W, "creature")
    accent = "#6f9a3a"
    limbs = [taper([(42, 44), (28, 35), (16, 29), (7, 28)], 16, 4), taper([(78, 44), (92, 35), (104, 29), (113, 28)], 16, 4),
             taper([(45, 86), (37, 95), (31, 102)], 12, 4), taper([(75, 86), (83, 95), (89, 102)], 12, 4), taper([(60, 94), (60, 105)], 6, 1.5)]
    for d in limbs:
        a.add(d, fill=INK)
    dots(a, [(20, 32), (28, 36), (100, 32), (92, 36), (37, 95), (83, 95)], 1.6)
    head = ellipse(60, 19, 11, 13)
    a.add(head, fill=INK)
    dots(a, [(55, 22), (65, 22), (60, 11)], 1.5)
    for x in (54.5, 65.5):
        a.add(circle(x, 15, 2.2), fill=CREAM)
        a.add(circle(x + 0.4, 15.2, 1.1), fill=INK)
    shell = ellipse(60, 60, 31, 37)
    a.add(shell, fill=accent)
    with a.group(clip=shell):
        for k in range(12):
            deg = k * 30
            a.line(f"M{pt(arc_pt(60, 60, 25.5, deg, 31.5))} L{pt(arc_pt(60, 60, 34, deg, 40))}", INK, 2)
        a.add(ellipse(60, 60, 25.5, 31.5), stroke=INK, width=2)
        hatch(a, ellipse(60, 60, 31, 37), angle=60, spacing=3.6, width=1.1, box=(60, 60, 96, 100))
    plates = [[(52, 31), (68, 31), (72, 42), (68, 52), (52, 52), (48, 42)],
              [(52, 52), (68, 52), (72, 63), (68, 74), (52, 74), (48, 63)],
              [(52, 74), (68, 74), (71, 84), (60, 91), (49, 84)]]
    for h in plates:
        a.add(poly(h), fill=CREAM, stroke=INK, width=2.6)
    for p, q in (((48, 42), (36, 38)), ((48, 63), (34.5, 63)), ((49, 84), (39, 87)), ((72, 42), (84, 38)), ((72, 63), (85.5, 63)), ((71, 84), (81, 87))):
        a.line(poly([p, q], closed=False), INK, 2.4)
    a.add(shell, stroke=INK, width=4)
    return a


def snake():
    a = Art("snake", W, W, "creature")
    accent = "#3d8fc4"
    c1, c2, r = (60, 38), (60, 86), 24
    body = f"M60,14 A24,24 0 0,0 60,62 A24,24 0 1,1 {f(60 + r * math.cos(math.radians(170)))},{f(86 + r * math.sin(math.radians(170)))}"
    paddle = blob([(33, 92), (31, 82), (32, 72), (38, 66), (40, 76), (41, 86)])
    a.add(paddle, fill=accent, stroke=INK, width=2.6)
    a.line(body, INK, 18)
    a.line(body, accent, 12)
    for c, ts in ((c1, (-102, -126, -150, -174, -198, -222, -246)), (c2, (-66, -42, -18, 6, 30, 54, 78, 102, 126, 150))):
        for t in ts:
            a0, a1 = math.radians(t - 6), math.radians(t + 6)
            p0 = (c[0] + r * math.cos(a0), c[1] + r * math.sin(a0))
            p1 = (c[0] + r * math.cos(a1), c[1] + r * math.sin(a1))
            a.line(f"M{pt(p0)} A24,24 0 0,1 {pt(p1)}", INK, 12, cap="butt")
    a.line(f"M{pt(arc_pt(60, 38, 22.5, -95))} A22.5,22.5 0 0,0 {pt(arc_pt(60, 38, 22.5, -250))}", CREAM, 1.4)
    a.line(f"M{pt(arc_pt(60, 86, 22.5, -80))} A22.5,22.5 0 1,1 {pt(arc_pt(60, 86, 22.5, 160))}", CREAM, 1.4)
    a.line("M86,14 L93,14 M93,14 L97,10.5 M93,14 L97,17.5", INKS["red"], 2)
    head = blob([(52, 8.5), (68, 4.5), (81, 7.5), (88, 14), (81, 20.5), (68, 23.5), (52, 19.5)])
    a.add(head, fill=INK)
    snout = blob([(75, 7.5), (83, 9.5), (87.5, 14), (83, 18.5), (75, 20.5), (79, 14)])
    a.add(snout, fill=accent)
    carve(a, head, [smooth([(56, 10), (66, 7.5), (74, 8)], closed=False)], 1.4)
    lino_eye(a, 71, 12, 2.8)
    return a


def remora():
    a = Art("remora", W, W, "creature")
    accent = "#b89b6a"
    host = "M0,0 H120 V20 C96,33 44,37 0,26 Z"
    a.add(host, fill=INK)
    carve(a, host, [f"M0,{y} C44,{y + 11} 96,{y + 7} 120,{y - 6}" for y in range(6, 26, 4)], 1.2)
    a.add("M0,26 C44,37 96,33 120,20 L120,25 C96,39 44,42 0,31 Z", fill=CREAM, stroke=INK, width=2)
    tail = blob([(24, 60), (12, 50), (6, 46), (9, 56), (10, 62), (9, 68), (6, 78), (12, 74), (24, 64)])
    a.add(tail, fill=INK)
    for fin in (blob([(40, 57), (50, 49), (62, 48.5), (70, 55)]), blob([(42, 66), (52, 74), (64, 74.5), (70, 68)])):
        a.add(fin, fill=INK)
    body = blob([(109, 66), (101, 58), (88, 55), (70, 54), (50, 55), (32, 58), (22, 62), (32, 66), (50, 69), (70, 70.5), (88, 70), (101, 68.5)])
    a.add(body, fill=accent)
    with a.group(clip=body):
        a.add("M0,60 L120,60 L120,65 L0,65 Z", fill=INK)
        for x in range(28, 104, 4):
            a.line(f"M{x},66 L{x - 2},72", INK, 1)
    a.add(body, stroke=INK, width=3.4)
    disc = ellipse(84, 53.6, 13, 3.8)
    a.add(disc, fill=CREAM, stroke=INK, width=2)
    for x in range(75, 95, 3):
        a.line(f"M{x},51 L{x},56.2", INK, 1.2)
    lino_eye(a, 98, 60.5, 3)
    a.line("M109,66 Q102,66.8 95,66", INK, 1.6)
    return a


# ---- Traders, trappers and tricksters ------------------------------------------------------------------


def crab():
    a = Art("crab", W, W, "creature")
    accent = "#d9803a"
    for (p, q, r) in (((56, 86), (46, 95), (40, 107)), ((63, 90), (57, 100), (53, 111)), ((70, 92), (68, 102), (68, 112))):
        a.add(taper([p, q], 6, 5), fill=INK)
        a.add(taper([q, r], 5, 2.4), fill=INK)
        a.add(circle(q[0], q[1], 1.6), fill=CREAM)
    shell = blob([(56, 88), (52, 70), (58, 48), (74, 32), (94, 28), (110, 40), (112, 60), (104, 78), (88, 90), (70, 94)])
    a.add(shell, fill=CREAM)
    spiral = []
    for i in range(64):
        t = i / 63
        ang = math.radians(-100 + t * 560)
        rr = 25 * (1 - t) + 3
        spiral.append((88 + rr * math.cos(ang), 58 + rr * math.sin(ang) * 0.9))
    with a.group(clip=shell):
        for t0 in (0.05, 0.3, 0.55):
            seg = spiral[int(t0 * 63): int(t0 * 63) + 12]
            a.add(taper(seg, 6, 2), fill=accent)
        with a.group(clip=ellipse(96, 84, 20, 12)):
            hatch(a, shell, angle=-30, spacing=4, width=1.1, box=(76, 72, 116, 96))
    a.line(smooth(spiral[::3], closed=False), INK, 2.6)
    a.add(shell, stroke=INK, width=4)
    for p, q in (((52, 70), (46, 54)), ((58, 70), (56, 52))):
        a.add(taper([p, q], 3.2, 2.6), fill=INK)
        lino_eye(a, q[0], q[1] - 2, 3.4)
    a.line(smooth([(50, 64), (40, 48), (30, 38)], closed=False), INK, 1.4)
    a.line(smooth([(56, 63), (52, 46), (48, 32)], closed=False), INK, 1.4)
    small = blob([(56, 82), (48, 86), (42, 94), (48, 97), (56, 92)])
    a.add(small, fill=accent, stroke=INK, width=2.6)
    claw = blob([(54, 72), (45, 64), (33, 60), (21, 62), (14, 70), (18, 80), (29, 85), (41, 83), (52, 80)])
    a.add(claw, fill=accent)
    dots(a, [(30, 66), (37, 64), (26, 75), (36, 74), (44, 72)], 1.6)
    a.add(claw, stroke=INK, width=3.4)
    pincer = blob([(23, 64), (14, 58), (7, 60), (9, 66), (17, 68)])
    a.add(pincer, fill=accent, stroke=INK, width=2.8)
    a.line(smooth([(13, 69), (22, 70), (30, 68)], closed=False), INK, 2)
    return a


def angler():
    a = Art("angler", W, W, "creature")
    accent = "#5560b8"
    # The glow, printed as rays around the bulb.
    a.add(circle(100, 21, 15), fill=CREAM, alpha=0.55)
    for k in range(12):
        deg = k * 30
        a.line(f"M{pt(arc_pt(100, 21, 9, deg))} L{pt(arc_pt(100, 21, 15 if k % 2 else 12, deg))}", INKS["yellow"], 2)
    a.add(taper([(62, 31), (65, 17), (76, 8.5), (90, 9), (99, 16)], 2.8, 2), fill=INK)
    for d in (blob([(26, 60), (13, 50), (8, 47), (10, 62), (8, 77), (13, 74), (26, 67)]), blob([(54, 80), (44, 87), (40, 95), (50, 92), (58, 86)])):
        a.add(d, fill=INK)
    body = blob([(98, 64), (96, 47), (86, 34), (70, 28), (52, 30), (36, 38), (26, 52), (24, 66), (30, 80), (44, 92), (62, 97), (80, 93), (93, 81)])
    a.add(body, fill=accent)
    hatch(a, body, angle=45, spacing=3.6, width=1.2, box=(50, 60, 100, 100))
    dots(a, [(44, 44), (38, 58), (52, 62), (60, 40), (46, 80), (70, 48), (34, 70)], 1.4)
    a.add(body, stroke=INK, width=4)
    mouth = blob([(98, 58), (90, 62), (80, 69), (71, 76), (80, 81), (91, 83), (99, 77)])
    a.add(mouth, fill=INK)
    for i in range(6):
        t = i / 5
        x, y = 96 - 22 * t, 60.5 + 15 * t
        a.add(poly([(x - 1.8, y - 0.8), (x + 1.8, y + 0.4), (x - 0.6, y + 5.2)]), fill=CREAM)
    for i in range(5):
        t = i / 4
        x, y = 76 + 21 * t, 79.5 + 1.2 * t
        a.add(poly([(x - 1.6, y + 0.5), (x + 1.6, y + 0.5), (x + 0.2, y - 4.6)]), fill=CREAM)
    lino_eye(a, 80, 46, 3.8)
    a.add(circle(100, 21, 5.4), fill="#fff3c4", stroke=INK, width=2)
    return a


def octopus():
    a = Art("octopus", W, W, "creature")
    accent = "#d4553a"
    arms = [
        [(44, 58), (30, 64), (20, 74), (18, 86), (24, 94), (32, 92), (30, 84)],
        [(50, 62), (42, 76), (38, 90), (42, 104), (50, 108), (54, 100)],
        [(57, 64), (57, 80), (60, 94), (66, 105), (73, 110), (76, 103)],
        [(66, 62), (78, 76), (84, 92), (82, 104)],
        [(70, 62), (84, 70), (96, 80), (104, 92), (111, 88), (108, 81)],
        [(78, 55), (92, 55), (104, 49), (110, 39), (105, 32), (100, 37)],
        [(42, 54), (28, 52), (16, 46), (10, 36), (15, 30), (20, 35)],
    ]
    for pts in arms:
        a.add(taper(pts, 11, 2.6), fill=INK)
        samples = spline_points(pts, 6)
        dots(a, [(x, y + 1.5) for x, y in samples[3:int(len(samples) * 0.75):3]], 1.7)
    head = blob([(60, 9), (76, 12), (88, 25), (90, 42), (84, 56), (72, 64), (60, 66), (48, 64), (36, 56), (30, 42), (32, 25), (44, 12)])
    a.add(head, fill=accent)
    carve(a, head, [f"M{x},0 Q{x + 14},40 {x + 4},80" for x in range(64, 110, 5)], 1.5, INK)
    carve(a, head, [smooth([(38, 38), (40, 24), (50, 15)], closed=False), smooth([(44, 40), (46, 28), (54, 20)], closed=False)], 1.8)
    a.add(head, stroke=INK, width=4)
    for x in (47, 73):
        a.add(ellipse(x, 50, 6.5, 5.5), fill=CREAM, stroke=INK, width=2.2)
        a.line(f"M{x - 3.6},50.5 H{x + 3.6}", INK, 2.8)
    return a


def cuttlefish():
    a = Art("cuttlefish", W, W, "creature")
    magenta, gold, teal = "#c64a8f", INKS["yellow"], INKS["teal"]
    skirt = []
    top = ring_points(60, 62, 44, 25, 185, 355, 14)
    bottom = ring_points(60, 62, 44, 25, 5, 175, 14)
    for i, p in enumerate(top + bottom):
        k = 1.0 if i % 2 == 0 else 0.9
        skirt.append((60 + (p[0] - 60) * k, 62 + (p[1] - 62) * k))
    fin = blob(skirt)
    a.add(fin, fill=INK)
    carve(a, fin, [blob([(60 + (x - 60) * 0.94, 62 + (y - 62) * 0.9) for x, y in skirt])], 1.4)
    for pts in ([(106, 60), (112, 54), (117, 49)], [(107, 62), (114, 60), (119, 58)], [(107, 64), (114, 66), (119, 68)], [(106, 66), (111, 72), (115, 76)]):
        a.add(taper(pts, 4.2, 1.4), fill=INK)
    mantle = blob([(94, 62), (88, 50), (74, 44), (54, 44), (36, 48), (26, 58), (26, 66), (36, 74), (54, 78), (74, 78), (88, 72)])
    a.add(mantle, fill=CREAM)
    with a.group(clip=mantle):
        for i, x in enumerate(range(28, 96, 9)):
            color = (magenta, gold, teal)[i % 3]
            a.add(taper([(x + 2, 42), (x - 2, 54), (x + 2, 66), (x - 2, 78)], 5.5, 5.5), fill=color)
    a.add(mantle, stroke=INK, width=4)
    head = blob([(91, 53), (102, 53), (108, 59), (107, 67), (97, 70), (91, 66)])
    a.add(head, fill=teal, stroke=INK, width=3)
    a.add(circle(98, 58.5, 4.6), fill=CREAM, stroke=INK, width=1.8)
    a.line("M94.4,57.5 L96.2,60.2 L98,58 L99.8,60.2 L101.6,57.5", INK, 1.8)
    return a


ALL = [shark, sardines, lionfish, starfish, coral, jellyfish, parrotfish, turtle, snake, remora, crab, angler, octopus, cuttlefish]

# Which portrait each faction key uses.
BY_FACTION = {
    "sharks": "shark", "sardines": "sardines", "lionfish": "lionfish", "starfish": "starfish", "coral": "coral",
    "jellyfish": "jellyfish", "parrotfish": "parrotfish", "turtles": "turtle", "snake": "snake", "remoras": "remora",
    "crabs": "crab", "anglers": "angler", "octopus": "octopus", "cuttlefish": "cuttlefish",
}
