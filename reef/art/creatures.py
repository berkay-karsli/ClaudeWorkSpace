"""The 14 faction portraits, 120 x 120, facing right where they face anywhere."""
import math

from artlib import (Art, blob, taper, circle, ellipse, poly, smooth, lin, rad, vgrad, lighten, darken, mix,
                    arc_pt, ring_points, spline_points, along, lerp, f, pt)

W = 120


def eye(a, x, y, r, iris=None, ink="#10161c"):
    """A friendly round eye: optional colored iris, dark pupil, two highlights."""
    if iris:
        a.add(circle(x, y, r), fill=iris, stroke=ink, width=r * 0.35)
        a.add(circle(x + r * 0.08, y, r * 0.62), fill=ink)
    else:
        a.add(circle(x, y, r), fill=ink)
    a.add(circle(x + r * 0.3, y - r * 0.32, r * 0.3), fill="#ffffff")
    a.add(circle(x - r * 0.28, y + r * 0.3, r * 0.13), fill="#ffffff", alpha=0.8)


def shine(a, points, w0=3.0, w1=1.0, alpha=0.45):
    a.add(taper(points, w0, w1), fill="#ffffff", alpha=alpha)


def outlined(a, shapes, fill, ink, width):
    """Several overlapping shapes that read as one: outline pass first, then fill pass on top."""
    for d in shapes:
        a.add(d, fill=ink, stroke=ink, width=width)
    for d in shapes:
        a.add(d, fill=fill)


# ---- Predators and swarms -------------------------------------------------------------------------


def shark():
    a = Art("shark", W, W, "creature")
    ink = "#17262e"
    body = vgrad(40, 82, "#8fb2c2", "#3e5d6c")
    # Tail, fins behind the body.
    a.add(blob([(36, 54), (25, 45), (15, 33), (9, 25), (13, 36), (18, 48), (21, 60), (18, 72), (13, 84), (9, 92), (19, 83), (28, 73), (36, 67)]),
          fill=vgrad(26, 90, "#6f93a3", "#34505d"), stroke=ink, width=2.2)
    a.add(blob([(54, 46), (60, 32), (66, 18), (70, 22), (74, 36), (84, 47)]), fill=vgrad(18, 48, "#7c9fb0", "#46687a"), stroke=ink, width=2.2)
    a.add(blob([(38, 52), (37, 44), (44, 50)]), fill="#46687a", stroke=ink, width=1.8)
    a.add(blob([(56, 74), (50, 86), (44, 92), (54, 88), (66, 78)]), fill="#3e5d6c", stroke=ink, width=2)
    # Body.
    a.add(blob([(111, 61), (102, 51), (86, 44), (68, 41), (50, 43), (36, 50), (26, 57), (26, 64), (38, 71), (54, 77),
                (72, 79), (90, 76), (103, 69)]), fill=body, stroke=ink, width=2.4)
    # White belly.
    a.add(blob([(105, 66), (92, 73), (72, 76.5), (54, 74.5), (38, 69), (52, 70), (72, 70.5), (90, 68)]),
          fill=vgrad(66, 78, "#f4f7f6", "#c8d5d8"))
    shine(a, [(52, 47), (68, 44), (86, 46)], 3.2, 1.2, 0.35)
    # Near pectoral fin.
    a.add(blob([(76, 71), (70, 84), (60, 97), (68, 95), (80, 84), (88, 73)]), fill=vgrad(70, 97, "#6f93a3", "#34505d"), stroke=ink, width=2.2)
    for x in (82, 86, 90):
        a.line(smooth([(x, 53), (x - 1.6, 59), (x, 65)], closed=False), darken("#3e5d6c", 0.35), 1.5)
    eye(a, 97, 55, 3.4)
    a.line(smooth([(106, 66), (99, 68.5), (91, 67.5)], closed=False), ink, 1.6)
    return a


def sardines():
    a = Art("sardines", W, W, "creature")
    ink = "#1e2c3d"

    def fish(tx, ty, s):
        with a.group(tx=tx, ty=ty, sx=s):
            a.add(blob([(-25, 0), (-33, -7), (-39, -11), (-36, -3), (-35, 0), (-36, 3), (-39, 11), (-33, 7)]),
                  fill="#6f8aa6", stroke=ink, width=1.6)
            a.add(blob([(31, 0), (21, -7.5), (4, -9.5), (-12, -7.5), (-25, -3), (-29, 0), (-25, 3), (-12, 7.5), (4, 9.5), (21, 7.5)]),
                  fill=vgrad(-10, 10, "#e8eef4", "#8fa0b3"), stroke=ink, width=1.8)
            a.add(blob([(29, -1.5), (20, -7), (4, -9), (-12, -7), (-24, -3), (-12, -3.5), (4, -3.8), (20, -3)]),
                  fill=vgrad(-10, -2, "#2f5379", "#5d82a8"))
            shine(a, [(-14, 2.4), (2, 3.4), (18, 2.4)], 2.2, 0.8, 0.6)
            for x in (-12, -5, 2, 9):
                a.add(circle(x, -2.2, 1.1), fill="#1f3a58", alpha=0.8)
            a.line(smooth([(17, -5), (15, 0), (17, 5)], closed=False), "#6d7f93", 1.2)
            eye(a, 23, -1.2, 2.6, iris="#d9e3ea")

    fish(58, 34, 1.0)
    fish(42, 62, 1.1)
    fish(70, 88, 0.95)
    for (x, y, r) in ((100, 22, 2.4), (106, 32, 1.6), (18, 40, 1.8), (98, 64, 1.4)):
        a.add(circle(x, y, r), fill="#ffffff", alpha=0.35, stroke="#ffffff", width=0.8)
    return a


def lionfish():
    a = Art("lionfish", W, W, "creature")
    ink = "#4a1206"
    red = "#c9502a"
    cream = "#fbeadb"

    def banded(base, tip, w0, w1):
        a.add(taper([base, tip], w0, w1), fill=cream, stroke=ink, width=1.1)
        for t0, t1 in ((0.12, 0.32), (0.48, 0.64), (0.8, 0.9)):
            p0, p1 = lerp(base, tip, t0), lerp(base, tip, t1)
            a.add(taper([p0, p1], w0 + (w1 - w0) * t0, w0 + (w1 - w0) * t1, start_cap=False), fill=red)

    # Pectoral fans, far one first.
    root = (58, 71)
    tips = [(56, 112), (43, 109), (31, 101), (22, 91), (17, 80)]
    a.add(blob([root] + [lerp(root, t, 0.92) for t in tips]), fill="#e0714a", alpha=0.6, stroke=ink, width=1.2)
    for t in tips:
        banded(root, t, 3.2, 1.2)
    # Dorsal spines with their membrane.
    bases = [(44, 53), (51, 50), (58, 48.5), (65, 48), (72, 48), (79, 49), (86, 51)]
    tops = [(30, 26), (38, 14), (50, 7), (63, 5), (76, 8), (88, 15), (97, 26)]
    a.add(smooth(bases + [lerp(b, t, 0.62) for b, t in zip(bases[::-1], tops[::-1])]), fill="#d9552f", alpha=0.5)
    for b, t in zip(bases, tops):
        banded(b, t, 3.4, 1.0)
    # Tail fan.
    a.add(blob([(34, 62), (22, 50), (11, 46), (8, 63), (11, 80), (22, 76), (34, 68)]), fill="#e98a62", alpha=0.8, stroke=ink, width=1.4)
    for y in (50, 57, 64, 71, 78):
        a.line(smooth([(32, 64), (20, (64 + y) / 2), (10, y)], closed=False), "#b8432a", 1.2)
    # Body and stripes.
    top = [(44, 52), (54, 48.5), (66, 47.5), (80, 48), (92, 53)]
    bot = [(44, 75), (54, 78.5), (66, 79.5), (80, 77.5), (92, 72)]
    a.add(blob([(101, 62), (94, 52), (80, 47), (62, 47), (46, 51), (35, 58), (31, 64), (37, 70), (50, 76), (66, 79.5), (82, 77.5), (95, 71)]),
          fill=vgrad(46, 80, "#ec8457", "#a33a20"), stroke=ink, width=2.4)
    for (tx, ty), (bx, by) in zip(top, bot):
        a.add(taper([(tx + 2, ty + 1.8), ((tx + bx) / 2 - 0.5, (ty + by) / 2), (bx - 2, by - 1.8)], 3.6, 3.0), fill=cream, alpha=0.95)
    shine(a, [(50, 55), (66, 51.5), (82, 53)], 2.6, 1, 0.3)
    # Near pectoral fin ray, over the body.
    a.add(taper([(64, 70), (56, 86), (48, 98)], 3.2, 1.2), fill=cream, stroke=ink, width=1)
    # Face: tentacle brows, eye, lips.
    a.add(taper([(89, 54), (88, 46), (84, 40)], 2.4, 1.0), fill=cream, stroke=ink, width=0.9)
    a.add(taper([(99, 66), (104, 72), (104, 78)], 2.2, 1.0), fill=cream, stroke=ink, width=0.9)
    eye(a, 91, 59, 3.4, iris="#f2c14e")
    a.line(smooth([(102, 64), (97, 66), (92, 65.5)], closed=False), ink, 1.5)
    return a


def starfish():
    a = Art("starfish", W, W, "creature")
    ink = "#4a0d2a"
    cx, cy = 60, 63
    body = rad(cx, cy, 52, (0, "#ffa3cf"), (0.45, "#e0659f"), (1, "#8f2a5c"))
    arm = taper([(cx, cy), (cx, cy - 26), (cx, cy - 50)], 27, 9.5, ease=0.9)
    angles = [0, 72, 144, 216, 288]
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            a.add(arm, fill=ink, stroke=ink, width=5)
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            a.add(arm, fill=body)
    a.add(circle(cx, cy, 15), fill=body)
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            a.add(taper([(cx, cy - 8), (cx, cy - 30), (cx, cy - 46)], 3.4, 1.4), fill="#7d1f4d", alpha=0.35)
            for i, (d, r) in enumerate(((16, 3.2), (25, 2.9), (34, 2.5), (42, 2.0))):
                a.add(circle(cx, cy - d, r), fill="#ffd0e6", stroke="#a33a70", width=0.9)
                a.add(circle(cx - 7.5 + i * 1.2, cy - d + 2, 1.3), fill="#ffd0e6", alpha=0.85)
                a.add(circle(cx + 7.5 - i * 1.2, cy - d + 2, 1.3), fill="#ffd0e6", alpha=0.85)
    a.add(circle(cx, cy, 6), fill="#ffd0e6", stroke="#a33a70", width=1.1)
    shine(a, [(40, 44), (48, 36), (56, 32)], 3, 1, 0.35)
    return a


# ---- Builders and drifters --------------------------------------------------------------------------


def coral():
    a = Art("coral", W, W, "creature")
    ink = "#6a1b27"
    a.add(blob([(20, 111), (34, 101), (60, 98), (86, 101), (101, 111), (60, 116)]), fill=vgrad(98, 116, "#9b8f80", "#5b5249"), stroke="#3c342d", width=2)
    branches = [
        ([(60, 108), (59, 94), (57, 80), (56, 68)], 13, 10),
        ([(57, 80), (48, 70), (42, 58), (40, 46)], 9, 6.5),
        ([(41, 53), (34, 46), (30, 38), (28, 27)], 6.5, 4.5),
        ([(40, 49), (44, 39), (45, 29), (43, 20)], 6.5, 4.5),
        ([(56, 70), (62, 58), (65, 44), (66, 29)], 9, 6),
        ([(65, 44), (72, 37), (79, 30), (85, 22)], 6.5, 4.5),
        ([(59, 92), (70, 85), (80, 76), (86, 62)], 9, 6),
        ([(84, 68), (90, 60), (94, 52), (97, 41)], 6, 4.2),
        ([(58, 94), (46, 87), (36, 80), (26, 70)], 8.5, 5.5),
        ([(33, 78), (25, 73), (19, 66), (15, 56)], 5.5, 4),
    ]
    shapes = [taper(p, w0, w1) for p, w0, w1 in branches]
    outlined(a, shapes, vgrad(18, 110, "#ffa6ad", "#c9404d"), ink, 4.4)
    for p, w0, w1 in branches:
        a.add(taper([(x - w0 * 0.18, y - 0.6) for x, y in p[1:]], w1 * 0.35, w1 * 0.2), fill="#ffe0e2", alpha=0.55)
    for p, _, w1 in branches:
        x, y = p[-1]
        for dx, dy in ((-2.6, -1.5), (2.6, -1.5), (0, -3.6), (0, 0)):
            a.add(circle(x + dx, y + dy, 1.9), fill="#fff1ec", stroke="#e56e7b", width=0.7)
    for x, y in ((50, 62), (62, 76), (72, 50), (48, 84), (78, 80), (36, 60)):
        a.add(circle(x, y, 1.3), fill="#fff1ec", alpha=0.8)
    return a


def jellyfish():
    a = Art("jellyfish", W, W, "creature")
    ink = "#3a175e"
    for i, x in enumerate(range(28, 95, 11)):
        pts = [(x, 60), (x - 4, 74), (x + 3, 88), (x - 3, 102), (x + 1, 116)]
        a.add(taper(pts, 2.4, 0.7), fill="#c6a2ee", alpha=0.85)
    for pts, w in (([(52, 60), (47, 76), (55, 90), (48, 106)], 8), ([(68, 60), (73, 78), (65, 92), (71, 108)], 8), ([(60, 60), (60, 80), (60, 98)], 6)):
        a.add(taper(pts, w, 2.6), fill=vgrad(60, 108, "#b584e2", "#7a45b0"), stroke=ink, width=1.3)
        a.add(taper([(x - 1.5, y) for x, y in pts], w * 0.3, 1), fill="#ecdcff", alpha=0.6)
    dome = ring_points(60, 58, 35, 40, 180, 360, 11)
    rim = []
    for i, x in enumerate(range(95, 24, -7)):
        rim.append((x, 61 if i % 2 == 0 else 65.5))
    a.add(blob(dome + rim), fill=rad(56, 30, 58, (0, "#f0e2ff"), (0.45, "#b58ae2"), (1, "#6a3a9a")), stroke=ink, width=2.4, alpha=0.95)
    a.add(blob(ring_points(60, 58, 26, 30, 190, 350, 7) + [(80, 56), (60, 54), (40, 56)]), fill="#ffffff", alpha=0.18)
    for x, y in ((47, 45), (60, 40), (73, 45)):
        a.add(ellipse(x, y, 4.6, 6.4), stroke="#f3e2ff", width=2.4, alpha=0.7)
    shine(a, [(33, 50), (36, 36), (46, 25), (56, 22)], 3.6, 1.4, 0.55)
    return a


def parrotfish():
    a = Art("parrotfish", W, W, "creature")
    ink = "#08433d"
    pink, yellow = "#f07aa8", "#f5c542"
    a.add(blob([(28, 55), (17, 43), (8, 36), (11, 50), (13, 60), (11, 70), (8, 84), (17, 77), (28, 66)]),
          fill=lin(8, 0, 28, 0, (0, pink), (0.5, "#2fb6a4"), (1, "#1b9a8a")), stroke=ink, width=2.2)
    a.add(blob([(40, 41), (50, 30), (66, 26), (82, 29), (93, 40), (80, 36), (64, 34), (50, 37)]), fill=pink, stroke=ink, width=1.8)
    a.add(blob([(46, 78), (56, 88), (70, 90), (80, 83), (68, 84), (56, 82)]), fill=pink, stroke=ink, width=1.8)
    a.add(blob([(105, 60), (100, 48), (88, 38), (70, 33), (52, 34), (38, 40), (28, 50), (25, 58), (28, 66), (38, 76), (54, 83), (72, 85), (90, 80), (101, 71)]),
          fill=vgrad(32, 86, "#5fe0cc", "#0e7568"), stroke=ink, width=2.4)
    for row, y in enumerate((45, 54, 63, 72)):
        for col in range(6):
            x = 40 + col * 9 + (4.5 if row % 2 else 0)
            dx, dy = (x - 64) / 40, (y - 59) / 25
            if dx * dx + dy * dy > 0.85:
                continue
            a.line(f"M{f(x - 4)},{f(y)} C{f(x - 4)},{f(y + 4.5)} {f(x + 4)},{f(y + 4.5)} {f(x + 4)},{f(y)}", "#a6f2e6", 1.3, alpha=0.7)
    shine(a, [(44, 42), (60, 37), (78, 38)], 3, 1, 0.35)
    a.add(taper([(84, 43), (90, 51), (95, 60)], 4, 2.2), fill=pink)
    a.add(taper([(82, 67), (89, 71), (97, 71)], 3.6, 2), fill=pink)
    a.add(blob([(72, 62), (63, 70), (58, 79), (67, 77), (78, 66)]), fill=yellow, stroke=ink, width=1.6)
    a.add(blob([(100, 54), (107, 57), (109, 63), (104, 67), (99, 63)]), fill="#f6f1df", stroke=ink, width=1.6)
    a.line("M100,60.5 L108.5,60.5", ink, 1.2)
    eye(a, 90, 53, 3.6, iris=yellow)
    return a


# ---- Wanderers ----------------------------------------------------------------------------------------


def turtle():
    a = Art("turtle", W, W, "creature")
    ink = "#2f2a0e"
    skin = vgrad(10, 110, "#c3c67a", "#6d7433")
    for pts, w0, w1 in (([(42, 44), (28, 35), (16, 29), (7, 28)], 16, 4), ([(78, 44), (92, 35), (104, 29), (113, 28)], 16, 4),
                        ([(45, 86), (37, 95), (31, 102)], 12, 4), ([(75, 86), (83, 95), (89, 102)], 12, 4),
                        ([(60, 94), (60, 105)], 6, 1.5)):
        a.add(taper(pts, w0, w1), fill=skin, stroke=ink, width=2)
    for x, y in ((20, 32), (28, 36), (100, 32), (92, 36), (37, 95), (83, 95)):
        a.add(circle(x, y, 1.6), fill="#565c23", alpha=0.7)
    a.add(ellipse(60, 19, 11, 13), fill=skin, stroke=ink, width=2.2)
    for x, y in ((55, 22), (65, 22), (60, 12)):
        a.add(circle(x, y, 1.5), fill="#565c23", alpha=0.6)
    eye(a, 54.5, 15, 1.9)
    eye(a, 65.5, 15, 1.9)
    shell = rad(52, 48, 46, (0, "#d9c77a"), (0.55, "#a58a3e"), (1, "#6a5522"))
    a.add(ellipse(60, 60, 31, 37), fill=shell, stroke=ink, width=2.6)
    a.add(ellipse(60, 60, 25.5, 31.5), stroke="#4d3f15", width=1.5, alpha=0.75)
    for deg in range(0, 360, 30):
        p1 = (60 + 25.5 * math.cos(math.radians(deg)), 60 + 31.5 * math.sin(math.radians(deg)))
        p2 = (60 + 31 * math.cos(math.radians(deg)), 60 + 37 * math.sin(math.radians(deg)))
        a.line(poly([p1, p2], closed=False), "#4d3f15", 1.3, alpha=0.75)
    plate = "#d8c77c"
    hexes = [[(52, 31), (68, 31), (72, 42), (68, 52), (52, 52), (48, 42)],
             [(52, 52), (68, 52), (72, 63), (68, 74), (52, 74), (48, 63)],
             [(52, 74), (68, 74), (71, 84), (60, 91), (49, 84)]]
    for h in hexes:
        a.add(poly(h), fill=plate, stroke="#4d3f15", width=1.6, alpha=0.9)
    for p, q in (((48, 42), (36, 38)), ((48, 63), (34.5, 63)), ((49, 84), (39, 87)), ((72, 42), (84, 38)), ((72, 63), (85.5, 63)), ((71, 84), (81, 87))):
        a.line(poly([p, q], closed=False), "#4d3f15", 1.6, alpha=0.9)
    a.add(taper([(40, 50), (44, 38), (52, 30)], 4, 1.6), fill="#ffffff", alpha=0.35)
    return a


def snake():
    a = Art("snake", W, W, "creature")
    ink = "#0b2233"
    band = "#163a57"
    light = "#8fd3ee"
    c1, c2, r = (60, 38), (60, 86), 24
    body = f"M60,14 A24,24 0 0,0 60,62 A24,24 0 1,1 {f(60 + r * math.cos(math.radians(170)))},{f(86 + r * math.sin(math.radians(170)))}"
    # Paddle tail.
    a.add(blob([(33, 92), (31, 82), (32, 72), (38, 66), (40, 76), (41, 86)]), fill=light, stroke=ink, width=2)
    a.add(blob([(33, 80), (32, 72), (38, 66), (39, 73)]), fill=band)
    a.line(body, ink, 17)
    a.line(body, light, 12.6)
    for t in (-102, -126, -150, -174, -198, -222, -246):
        a0, a1 = math.radians(t - 6), math.radians(t + 6)
        p0 = (c1[0] + r * math.cos(a0), c1[1] + r * math.sin(a0))
        p1 = (c1[0] + r * math.cos(a1), c1[1] + r * math.sin(a1))
        a.line(f"M{pt(p0)} A24,24 0 0,1 {pt(p1)}", band, 12.6, cap="butt")
    for t in (-66, -42, -18, 6, 30, 54, 78, 102, 126, 150):
        a0, a1 = math.radians(t - 6), math.radians(t + 6)
        p0 = (c2[0] + r * math.cos(a0), c2[1] + r * math.sin(a0))
        p1 = (c2[0] + r * math.cos(a1), c2[1] + r * math.sin(a1))
        a.line(f"M{pt(p0)} A24,24 0 0,1 {pt(p1)}", band, 12.6, cap="butt")
    a.line(f"M{pt(arc_pt(60, 38, 21, -95))} A21,21 0 0,0 {pt(arc_pt(60, 38, 21, -200))}", "#ffffff", 2.2, alpha=0.35)
    a.line(f"M{pt(arc_pt(60, 86, 21, -60))} A21,21 0 0,1 {pt(arc_pt(60, 86, 21, 10))}", "#ffffff", 2.2, alpha=0.35)
    # Head, looking right, tongue out.
    a.line("M86,14 L93,14 M93,14 L97,10.5 M93,14 L97,17.5", "#e0364f", 1.8)
    a.add(blob([(52, 8.5), (68, 4.5), (81, 7.5), (88, 14), (81, 20.5), (68, 23.5), (52, 19.5)]), fill=vgrad(4, 24, "#2a5578", "#10283d"), stroke=ink, width=2.2)
    a.add(blob([(75, 7.5), (83, 9.5), (87.5, 14), (83, 18.5), (75, 20.5), (79, 14)]), fill=light)
    a.add(taper([(56, 9.5), (66, 7), (74, 7.5)], 2.2, 1), fill="#ffffff", alpha=0.3)
    eye(a, 71, 11.5, 2.7, iris="#e8f6ff")
    return a


def remora():
    a = Art("remora", W, W, "creature")
    ink = "#2a241c"
    # The host: a shark's belly passing overhead.
    a.add(f"M0,0 H120 V18 C96,31 44,35 0,24 Z", fill=vgrad(0, 34, "#6d8e9d", "#46636f"))
    a.add(f"M0,24 C44,35 96,31 120,18 L120,24 C96,36 44,40 0,30 Z", fill="#dfe8e9", alpha=0.85)
    for x in (26, 50, 74, 98):
        a.line(smooth([(x, 26), (x + 3, 30), (x + 1, 34)], closed=False), "#46636f", 1.2, alpha=0.6)
    body = vgrad(52, 72, "#c2b8a4", "#6b6356")
    a.add(blob([(24, 60), (12, 50), (6, 46), (9, 56), (10, 62), (9, 68), (6, 78), (12, 74), (24, 64)]), fill=body, stroke=ink, width=2)
    a.add(blob([(40, 57), (50, 49), (62, 48.5), (70, 55)]), fill="#8c826f", stroke=ink, width=1.6)
    a.add(blob([(42, 66), (52, 74), (64, 74.5), (70, 68)]), fill="#8c826f", stroke=ink, width=1.6)
    a.add(blob([(109, 66), (101, 58), (88, 55), (70, 54), (50, 55), (32, 58), (22, 62), (32, 66), (50, 69), (70, 70.5), (88, 70), (101, 68.5)]),
          fill=body, stroke=ink, width=2.4)
    a.add(taper([(26, 62), (50, 61.5), (76, 62.5), (99, 63)], 3.4, 2.4), fill="#3a3229", alpha=0.85)
    a.add(taper([(30, 59), (52, 58.4), (78, 59), (96, 59.6)], 1.4, 1.0), fill="#f4efe4", alpha=0.7)
    a.add(taper([(30, 65.5), (52, 65.4), (78, 66.2), (96, 66.6)], 1.4, 1.0), fill="#f4efe4", alpha=0.7)
    a.add(ellipse(84, 53.6, 13, 3.8), fill="#e5dccb", stroke=ink, width=1.6)
    for x in range(75, 95, 3):
        a.line(f"M{x},51 L{x},56.2", "#7e7462", 1.1)
    eye(a, 98, 60.5, 2.7, iris="#e9dfc9")
    a.line(smooth([(109, 66), (102, 66.8), (95, 66)], closed=False), ink, 1.4)
    return a


# ---- Traders, trappers and tricksters ------------------------------------------------------------------


def crab():
    a = Art("crab", W, W, "creature")
    ink = "#4a2208"
    leg = vgrad(80, 112, "#ef9a5c", "#b4531f")
    for (p, q, r) in (((56, 86), (46, 95), (40, 107)), ((63, 90), (57, 100), (53, 111)), ((70, 92), (68, 102), (68, 112))):
        a.add(taper([p, q], 5.5, 4.4), fill=leg, stroke=ink, width=1.6)
        a.add(taper([q, r], 4.4, 2.2), fill=leg, stroke=ink, width=1.6)
        a.add(circle(q[0], q[1], 1.6), fill="#ffd9b8")
    # The shell it lives in.
    shell = rad(84, 48, 52, (0, "#fff0d6"), (0.5, "#e2b074"), (1, "#9c6630"))
    a.add(blob([(56, 88), (52, 70), (58, 48), (74, 32), (94, 28), (110, 40), (112, 60), (104, 78), (88, 90), (70, 94)]), fill=shell, stroke=ink, width=2.4)
    spiral = []
    for i in range(0, 64):
        t = i / 63
        ang = math.radians(-100 + t * 560)
        r = 25 * (1 - t) + 3
        spiral.append((88 + r * math.cos(ang), 58 + r * math.sin(ang) * 0.9))
    a.line(smooth(spiral[::3], closed=False), "#8a5526", 2.2)
    for t0 in (0.08, 0.32, 0.56):
        seg = spiral[int(t0 * 63): int(t0 * 63) + 9]
        a.add(taper(seg, 3.6, 1.2), fill="#b97a3c", alpha=0.6)
    a.add(taper([(64, 50), (74, 38), (88, 33)], 4, 1.4), fill="#ffffff", alpha=0.45)
    # Eyes on stalks, antennae.
    a.line(smooth([(50, 64), (40, 48), (30, 38)], closed=False), "#b4531f", 1.2)
    a.line(smooth([(56, 63), (52, 46), (48, 32)], closed=False), "#b4531f", 1.2)
    for p, q in (((52, 70), (46, 54)), ((58, 70), (56, 52))):
        a.add(taper([p, q], 3, 2.4), fill=leg, stroke=ink, width=1.3)
        eye(a, q[0], q[1] - 1.5, 3.2)
    # Small claw, then the big one.
    a.add(blob([(56, 82), (48, 86), (42, 94), (48, 97), (56, 92)]), fill=leg, stroke=ink, width=1.8)
    claw = vgrad(58, 88, "#f2a064", "#b24f1c")
    a.add(blob([(54, 72), (45, 64), (33, 60), (21, 62), (14, 70), (18, 80), (29, 85), (41, 83), (52, 80)]), fill=claw, stroke=ink, width=2.2)
    a.add(blob([(23, 64), (14, 58), (7, 60), (9, 66), (17, 68)]), fill=claw, stroke=ink, width=2)
    a.line(smooth([(13, 69), (22, 70), (30, 68)], closed=False), ink, 1.6)
    for x, y in ((30, 66), (37, 64), (26, 75), (36, 74), (44, 72)):
        a.add(circle(x, y, 1.5), fill="#ffe1c6", alpha=0.9)
    a.add(taper([(22, 64), (32, 62), (42, 64)], 2.4, 1), fill="#ffffff", alpha=0.45)
    return a


def angler():
    a = Art("angler", W, W, "creature")
    ink = "#0f1030"
    a.add(circle(100, 21, 19), fill=rad(100, 21, 19, (0, "#fff6b0", 0.95), (0.35, "#f6d64a", 0.45), (1, "#f6d64a", 0)))
    a.add(taper([(62, 31), (65, 17), (76, 8.5), (90, 9), (99, 16)], 2.8, 1.8), fill="#4a4fae", stroke=ink, width=1)
    a.add(blob([(26, 60), (13, 50), (8, 47), (10, 62), (8, 77), (13, 74), (26, 67)]), fill="#2c3078", stroke=ink, width=2)
    body = rad(58, 48, 58, (0, "#6a70d0"), (0.55, "#343a92"), (1, "#1a1c4c"))
    a.add(blob([(98, 64), (96, 47), (86, 34), (70, 28), (52, 30), (36, 38), (26, 52), (24, 66), (30, 80), (44, 92), (62, 97), (80, 93), (93, 81)]),
          fill=body, stroke=ink, width=2.4)
    for x, y, r in ((44, 44, 1.4), (38, 58, 1.1), (52, 62, 1.3), (60, 40, 1.1), (46, 80, 1.2), (70, 48, 1), (34, 70, 1)):
        a.add(circle(x, y, r), fill="#b6bbff", alpha=0.35)
    mouth = blob([(98, 58), (90, 62), (80, 69), (71, 76), (80, 81), (91, 83), (99, 77)])
    a.add(mouth, fill=rad(88, 72, 18, (0, "#6b1830"), (1, "#26060f")), stroke=ink, width=2)
    for i in range(6):
        t = i / 5
        x, y = 96 - 22 * t, 60.5 + 15 * t
        a.add(poly([(x - 1.8, y - 0.8), (x + 1.8, y + 0.4), (x - 0.6, y + 5.2)]), fill="#f3f1e6", stroke=ink, width=0.6)
    for i in range(5):
        t = i / 4
        x, y = 76 + 21 * t, 79.5 + 1.2 * t
        a.add(poly([(x - 1.6, y + 0.5), (x + 1.6, y + 0.5), (x + 0.2, y - 4.6)]), fill="#f3f1e6", stroke=ink, width=0.6)
    a.add(blob([(54, 80), (44, 87), (40, 95), (50, 92), (58, 86)]), fill="#23276a", stroke=ink, width=1.6)
    a.add(taper([(36, 46), (48, 35), (64, 31)], 3.4, 1.2), fill="#ffffff", alpha=0.25)
    eye(a, 80, 46, 3.4, iris="#dfe2ff")
    a.add(circle(100, 21, 5.2), fill=rad(99, 20, 6, (0, "#ffffff"), (0.6, "#fff3a0"), (1, "#f2cc3a")), stroke="#a8861c", width=1)
    return a


def octopus():
    a = Art("octopus", W, W, "creature")
    ink = "#4a0e18"
    far = vgrad(40, 110, "#b8404f", "#6e1826")
    near = vgrad(40, 110, "#e06a7b", "#8e2233")
    back = [[(66, 62), (78, 76), (84, 92), (80, 104), (73, 108), (72, 101)]]
    front = [
        [(44, 58), (30, 64), (20, 74), (18, 86), (24, 94), (32, 92), (30, 85)],
        [(50, 62), (42, 76), (38, 90), (42, 104), (50, 108), (54, 101)],
        [(57, 64), (57, 80), (60, 94), (66, 105), (73, 110), (76, 103)],
        [(70, 62), (84, 70), (96, 80), (104, 92), (111, 88), (108, 81)],
        [(78, 55), (92, 55), (104, 49), (110, 39), (105, 32), (100, 37)],
        [(42, 54), (28, 52), (16, 46), (10, 36), (15, 30), (20, 35)],
    ]
    for pts in back:
        a.add(taper(pts, 9, 2.2), fill=far, stroke=ink, width=1.8)
    for pts in front:
        a.add(taper(pts, 10, 2.4), fill=near, stroke=ink, width=1.8)
        samples = spline_points(pts, 6)
        n = len(samples)
        for i in range(2, int(n * 0.72), 3):
            p, q = samples[i], samples[min(i + 1, n - 1)]
            dx, dy = q[0] - p[0], q[1] - p[1]
            ln = math.hypot(dx, dy) or 1
            w = 10 - 7.6 * i / (n - 1)
            sx, sy = p[0] + dy / ln * w * 0.18, p[1] - dx / ln * w * 0.18
            a.add(circle(sx, sy, max(0.9, w * 0.16)), fill="#fbd2da", stroke="#b8404f", width=0.5)
    mantle = rad(52, 26, 50, (0, "#f08a98"), (0.5, "#c23a4e"), (1, "#7a1c2a"))
    a.add(blob([(60, 9), (76, 12), (88, 25), (90, 42), (84, 56), (72, 64), (60, 66), (48, 64), (36, 56), (30, 42), (32, 25), (44, 12)]),
          fill=mantle, stroke=ink, width=2.4)
    for x, y, r in ((50, 24, 2.2), (68, 22, 1.8), (76, 36, 2), (42, 38, 1.6), (60, 34, 1.4)):
        a.add(circle(x, y, r), fill="#7a1c2a", alpha=0.45)
    a.add(taper([(38, 36), (42, 22), (54, 14)], 5, 1.8), fill="#ffffff", alpha=0.3)
    for x in (46, 74):
        a.add(ellipse(x, 50, 6, 5), fill="#fff4d6", stroke=ink, width=1.6)
        a.add(ellipse(x, 50.4, 3.8, 1.5), fill="#1a0a0e")
        a.add(circle(x + 2, 48.4, 1), fill="#ffffff")
    return a


def cuttlefish():
    a = Art("cuttlefish", W, W, "creature")
    with a.group(sx=1.14, px=64, py=62, tx=-4):
        _cuttlefish(a)
    return a


def _cuttlefish(a):
    ink = "#3a1430"
    magenta, gold, teal = "#c64a8f", "#d9a02a", "#2d8f9a"
    skirt = []
    top = ring_points(60, 62, 44, 25, 185, 355, 14)
    bottom = ring_points(60, 62, 44, 25, 5, 175, 14)
    for i, p in enumerate(top + bottom):
        k = 1.0 if i % 2 == 0 else 0.9
        skirt.append((60 + (p[0] - 60) * k, 62 + (p[1] - 62) * k))
    a.add(blob(skirt), fill=lin(16, 0, 104, 0, (0, "#f3b6da"), (0.5, "#f6dc9a"), (1, "#a8e2e6")), stroke=ink, width=1.6, alpha=0.9)
    for pts in ([(106, 60), (112, 54), (117, 49)], [(107, 62), (114, 60), (119, 58)], [(107, 64), (114, 66), (119, 68)],
                [(106, 66), (111, 72), (115, 76)]):
        a.add(taper(pts, 4.2, 1.4), fill=vgrad(50, 78, "#f2c46a", "#b9801c"), stroke=ink, width=1.1)
    mantle = lin(24, 0, 96, 0, (0, magenta), (0.5, gold), (1, teal))
    a.add(blob([(94, 62), (88, 50), (74, 44), (54, 44), (36, 48), (26, 58), (26, 66), (36, 74), (54, 78), (74, 78), (88, 72)]),
          fill=mantle, stroke=ink, width=2.4)
    for i, x in enumerate(range(34, 90, 8)):
        pts = [(x + 2, 47), (x - 1, 55), (x + 2, 63), (x - 1, 71), (x + 1, 76)]
        a.add(taper(pts, 2.6, 1.8), fill="#2a0c24", alpha=0.28)
    a.add(taper([(36, 52), (54, 47), (74, 47.5), (86, 52)], 3.4, 1.2), fill="#ffffff", alpha=0.35)
    a.add(blob([(91, 53), (102, 53), (108, 59), (107, 67), (97, 70), (91, 66)]), fill=vgrad(52, 70, "#4ab3bc", "#1f6c75"), stroke=ink, width=2)
    a.add(circle(98, 58.5, 4.6), fill="#f5d36b", stroke=ink, width=1.2)
    a.line("M94.4,57.5 L96.2,60.2 L98,58 L99.8,60.2 L101.6,57.5", "#15090f", 1.8)


ALL = [shark, sardines, lionfish, starfish, coral, jellyfish, parrotfish, turtle, snake, remora, crab, angler, octopus, cuttlefish]

# Which portrait each faction key uses.
BY_FACTION = {
    "sharks": "shark", "sardines": "sardines", "lionfish": "lionfish", "starfish": "starfish", "coral": "coral",
    "jellyfish": "jellyfish", "parrotfish": "parrotfish", "turtles": "turtle", "snake": "snake", "remoras": "remora",
    "crabs": "crab", "anglers": "angler", "octopus": "octopus", "cuttlefish": "cuttlefish",
}
