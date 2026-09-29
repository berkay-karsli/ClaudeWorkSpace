"""
The 14 faction portraits, 120 x 120, in one cute style: round chubby bodies, big sparkly eyes,
rosy cheeks, small smiles, soft bright colors and warm dark outlines.
"""
import math

from artlib import (Art, blob, taper, circle, ellipse, poly, smooth, lin, rad, vgrad, lighten, darken, mix,
                    arc_pt, ring_points, spline_points, along, lerp, f, pt)

W = 120
EYE = "#2a1b2e"
BLUSH = "#ff8fa8"


def eye(a, x, y, r, iris=None, ink="#10161c"):
    """A small friendly eye for pieces and icons: optional colored iris, dark pupil, highlights."""
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


# ---- The cute face ------------------------------------------------------------------------------


def big_eye(a, x, y, r):
    """A big glossy eye: a tall dark oval with a large sparkle and a small one."""
    a.add(ellipse(x, y, r * 0.84, r), fill=rad(x - r * 0.25, y - r * 0.35, r * 1.5, (0, "#5a4166"), (1, EYE)))
    a.add(ellipse(x + r * 0.26, y - r * 0.36, r * 0.38, r * 0.34), fill="#ffffff")
    a.add(circle(x - r * 0.3, y + r * 0.42, r * 0.17), fill="#ffffff", alpha=0.9)


def blush(a, x, y, rx=5.5, ry=3.2):
    a.add(ellipse(x, y, rx, ry), fill=BLUSH, alpha=0.6)


def smile(a, x, y, w, ink, width=2.2):
    a.line(f"M{f(x - w)},{f(y)} Q{f(x)},{f(y + w * 0.95)} {f(x + w)},{f(y)}", ink, width)


def face(a, cx, cy, gap, r, ink, mouth=5.0, cheeks=True, mouth_dy=None):
    """Two big eyes [gap] apart around (cx, cy), rosy cheeks below them and a small smile."""
    big_eye(a, cx - gap / 2, cy, r)
    big_eye(a, cx + gap / 2, cy, r)
    if cheeks:
        blush(a, cx - gap / 2 - r * 0.6, cy + r * 1.35, r * 0.8, r * 0.48)
        blush(a, cx + gap / 2 + r * 0.6, cy + r * 1.35, r * 0.8, r * 0.48)
    if mouth:
        smile(a, cx, cy + (mouth_dy if mouth_dy is not None else r * 1.2), mouth, ink)


def gloss(a, cx, cy, rx, ry, alpha=0.4):
    """The soft round highlight on the upper left of a chubby body."""
    a.add(ellipse(cx, cy, rx, ry), fill="#ffffff", alpha=alpha)


def bubbles(a, pts):
    for x, y, r in pts:
        a.add(circle(x, y, r), fill="#ffffff", alpha=0.25, stroke="#ffffff", width=1)
        a.add(circle(x - r * 0.35, y - r * 0.35, r * 0.25), fill="#ffffff", alpha=0.8)


# ---- Predators and swarms -------------------------------------------------------------------------


def shark():
    a = Art("shark", W, W, "creature")
    ink = "#22384a"
    fin = vgrad(20, 100, "#8cc3db", "#4d85a0")
    a.add(blob([(34, 58), (20, 44), (10, 36), (12, 50), (16, 60), (12, 72), (12, 84), (22, 74), (34, 68)]), fill=fin, stroke=ink, width=3)
    a.add(blob([(46, 42), (52, 26), (58, 16), (65, 18), (70, 30), (76, 42)]), fill=fin, stroke=ink, width=3)
    a.add(blob([(108, 64), (104, 46), (90, 36), (68, 34), (48, 38), (32, 50), (28, 64), (34, 76), (50, 86), (72, 90), (94, 86), (106, 76)]),
          fill=vgrad(32, 92, "#a8d8ec", "#5b95b0"), stroke=ink, width=3)
    a.add(blob([(104, 72), (94, 82), (74, 87), (54, 84), (40, 76), (56, 76), (76, 76), (94, 74)]), fill="#f6fbfd")
    gloss(a, 56, 46, 13, 6)
    a.add(blob([(58, 80), (50, 92), (46, 100), (58, 98), (70, 86)]), fill=fin, stroke=ink, width=2.6)
    for x in (54, 59):
        a.line(smooth([(x, 55), (x - 1.5, 60), (x, 65)], closed=False), "#3f7690", 1.8)
    face(a, 86, 58, 17, 6.2, ink, mouth=0)
    blush(a, 72, 68, 4.6, 2.8)
    blush(a, 101, 67, 4.2, 2.6)
    a.line("M80,70 Q86,76 93,70", ink, 2.2)
    for x in (83, 89):
        a.add(poly([(x - 1.6, 71.6), (x + 1.6, 71.6), (x, 74.4)]), fill="#ffffff", stroke=ink, width=0.8)
    return a


def sardines():
    a = Art("sardines", W, W, "creature")
    ink = "#243a55"

    def fish(tx, ty, s, blink=False):
        with a.group(tx=tx, ty=ty, sx=s):
            a.add(blob([(-17, 0), (-27, -9), (-29, -3), (-28, 0), (-29, 3), (-27, 9)]), fill="#86a6c6", stroke=ink, width=2)
            a.add(blob([(21, 1), (16, -9), (3, -12.5), (-10, -10), (-19, -4), (-20, 2), (-12, 9), (2, 12), (15, 10)]),
                  fill=vgrad(-12, 12, "#f3f7fb", "#a9bcd2"), stroke=ink, width=2.2)
            a.add(blob([(19, -3), (14, -9), (2, -12), (-10, -9.5), (-17, -4), (-6, -5), (8, -5.5)]), fill=vgrad(-12, -4, "#4c79a8", "#7aa2c8"))
            gloss(a, -4, -7, 7, 2.2, 0.5)
            for x in (-12, -6, 0):
                a.add(circle(x, -1.5, 1.1), fill="#6e8fb3", alpha=0.8)
            if blink:
                a.line("M6,-2 Q9.5,1 13,-2", EYE, 2)
            else:
                big_eye(a, 9.5, -2, 4.4)
            blush(a, 12, 5, 3, 1.8)
            smile(a, 17.5, 3, 2, ink, 1.6)

    fish(58, 34, 1.15)
    fish(40, 64, 1.3)
    fish(76, 92, 1.1, blink=True)
    bubbles(a, [(100, 22, 3.2), (107, 34, 2), (18, 38, 2.4), (100, 62, 1.8)])
    return a


def lionfish():
    a = Art("lionfish", W, W, "creature")
    ink = "#5a1f10"
    orange, cream = "#ff8a54", "#fff1e0"
    # A crown of round-tipped spines along the back.
    for i, deg in enumerate(range(205, 345, 22)):
        base = arc_pt(60, 64, 26, deg, 21)
        tip = arc_pt(60, 64, 50, deg, 44)
        a.add(taper([base, tip], 6, 3.6), fill=cream if i % 2 == 0 else "#ffb58c", stroke=ink, width=1.6)
        a.add(circle(tip[0], tip[1], 3.4), fill="#ff6f61", stroke=ink, width=1.4)
    # Fan fins below, like petals.
    for ang in (15, 45, 75):
        with a.group(rot=ang, px=52, py=76):
            a.add(ellipse(52, 94, 8, 17), fill=rad(52, 84, 22, (0, cream), (1, orange)), stroke=ink, width=2)
            a.line("M52,82 L52,106", "#e4683f", 1.4)
    a.add(blob([(34, 62), (22, 52), (14, 50), (14, 64), (14, 78), (22, 76), (34, 68)]), fill=rad(18, 64, 20, (0, cream), (1, orange)), stroke=ink, width=2.4)
    a.add(ellipse(60, 64, 31, 24), fill=vgrad(40, 88, "#ffae7a", "#f06a3c"), stroke=ink, width=3)
    for x, w in ((42, 3.2), (52, 3.6), (62, 3.6), (72, 3.2)):
        top = 64 - 24 * math.sqrt(max(0.0, 1 - ((x - 60) / 31) ** 2)) + 2
        bot = 64 + 24 * math.sqrt(max(0.0, 1 - ((x - 60) / 31) ** 2)) - 2
        a.add(taper([(x + 2, top), (x - 1, (top + bot) / 2), (x + 1, bot)], w, w * 0.8), fill=cream, alpha=0.9)
    gloss(a, 48, 52, 11, 5)
    face(a, 78, 60, 15, 5.8, ink, mouth=0)
    a.add(ellipse(85, 72, 3, 2.4), fill="#c2412a", stroke=ink, width=1.4)
    return a


def starfish():
    a = Art("starfish", W, W, "creature")
    ink = "#6a1d43"
    cx, cy = 60, 63
    body = rad(cx, cy - 6, 56, (0, "#ffc4df"), (0.55, "#ff86b8"), (1, "#e05592"))
    arm = taper([(cx, cy), (cx, cy - 24), (cx, cy - 47)], 34, 18, ease=1.1)
    angles = [0, 72, 144, 216, 288]
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            a.add(arm, fill=ink, stroke=ink, width=6)
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            a.add(arm, fill=body)
    a.add(circle(cx, cy, 20), fill=body)
    for ang in angles:
        with a.group(rot=ang, px=cx, py=cy):
            for d, r in ((30, 3.2), (39, 2.6)):
                a.add(circle(cx, cy - d, r), fill="#fff0f7", alpha=0.9)
            a.add(circle(cx - 5, cy - 24, 1.6), fill="#fff0f7", alpha=0.7)
            a.add(circle(cx + 5, cy - 24, 1.6), fill="#fff0f7", alpha=0.7)
    gloss(a, 44, 34, 6, 3.4, 0.5)
    face(a, cx, cy - 2, 17, 6.2, ink, mouth=5)
    return a


# ---- Builders and drifters --------------------------------------------------------------------------


def coral():
    a = Art("coral", W, W, "creature")
    ink = "#7a2233"
    a.add(ellipse(60, 108, 44, 7), fill="#e9d3a8", stroke="#9d7a44", width=2)
    pink = vgrad(10, 104, "#ffb8c0", "#f06a7e")
    branches = [
        ([(34, 68), (24, 52), (20, 38)], 12, 9.5),
        ([(48, 58), (44, 40), (46, 24)], 12, 9.5),
        ([(64, 56), (70, 38), (68, 22)], 12, 9.5),
        ([(82, 62), (94, 48), (100, 34)], 12, 9.5),
        # Side branches, so it reads as branching coral rather than fingers.
        ([(25, 52), (14, 46), (10, 36)], 9, 8),
        ([(45, 42), (34, 34), (30, 22)], 9, 8),
        ([(69, 40), (80, 30), (84, 18)], 9, 8),
        ([(92, 50), (104, 50), (110, 42)], 8.5, 7.5),
    ]
    mound = blob([(22, 104), (18, 86), (26, 68), (42, 57), (60, 54), (78, 57), (94, 68), (102, 86), (98, 104)])
    shapes = [taper(p, w0, w1) for p, w0, w1 in branches] + [mound]
    outlined(a, shapes, pink, ink, 5.5)
    for p, _, w1 in branches:
        x, y = p[-1]
        for k in range(5):
            px, py = arc_pt(x, y, 4.2, -90 + k * 72)
            a.add(circle(px, py, 2.6), fill="#fff4f6", stroke="#ff8f9f", width=0.8)
        a.add(circle(x, y, 2.2), fill="#ffd66b")
    gloss(a, 40, 70, 9, 5)
    for x, y in ((32, 94), (88, 92), (58, 100), (78, 100)):
        a.add(circle(x, y, 2), fill="#fff4f6", alpha=0.7)
    face(a, 60, 80, 20, 6.8, ink, mouth=5.5)
    return a


def jellyfish():
    a = Art("jellyfish", W, W, "creature")
    ink = "#4a2470"
    for i, x in enumerate((32, 44, 76, 88)):
        curl = 1 if i % 2 == 0 else -1
        pts = [(x, 62), (x - 3 * curl, 76), (x + 3 * curl, 90), (x - 2 * curl, 102), (x + 4 * curl, 108), (x + 6 * curl, 102)]
        a.add(taper(pts, 4, 2), fill="#d7b8ff", stroke=ink, width=1.4)
    for pts in ([(54, 62), (50, 78), (57, 92), (51, 106)], [(66, 62), (71, 80), (64, 94), (70, 108)]):
        a.add(taper(pts, 10, 5), fill=vgrad(60, 108, "#e9d5ff", "#a57be0"), stroke=ink, width=1.8)
    dome = ring_points(60, 60, 38, 44, 180, 360, 11)
    rim = [(98, 62), (91, 67), (84, 63), (77, 67), (70, 63), (63, 67), (56, 63), (49, 67), (42, 63), (35, 67), (28, 63), (22, 62)]
    a.add(blob(dome + rim), fill=rad(54, 32, 64, (0, "#fbf3ff"), (0.45, "#d4b0ff"), (1, "#9c6fd8")), stroke=ink, width=3)
    gloss(a, 42, 32, 10, 6, 0.55)
    a.add(circle(80, 30, 2.5), fill="#ffffff", alpha=0.7)
    face(a, 60, 45, 22, 7, ink, mouth=5.5)
    return a


def parrotfish():
    a = Art("parrotfish", W, W, "creature")
    ink = "#0f4d46"
    pink, yellow = "#ff8fb8", "#ffd35a"
    a.add(blob([(28, 56), (16, 44), (10, 40), (12, 54), (14, 62), (12, 72), (10, 84), (16, 80), (28, 68)]),
          fill=lin(10, 0, 28, 0, (0, pink), (1, "#3cc9b4")), stroke=ink, width=2.6)
    a.add(blob([(44, 40), (54, 28), (70, 25), (84, 30), (92, 42), (78, 38), (62, 36)]), fill=pink, stroke=ink, width=2.4)
    a.add(blob([(104, 62), (100, 46), (86, 36), (66, 33), (46, 36), (32, 46), (26, 60), (32, 74), (46, 84), (66, 88), (86, 84), (100, 74)]),
          fill=vgrad(32, 90, "#7ff0dc", "#1fa290"), stroke=ink, width=3)
    for row, y in enumerate((52, 62, 72)):
        for col in range(5):
            x = 38 + col * 9 + (4.5 if row % 2 else 0)
            dx, dy = (x - 60) / 36, (y - 61) / 25
            if dx * dx + dy * dy > 0.8 or x > 76:
                continue
            a.line(f"M{f(x - 3.6)},{f(y)} Q{f(x)},{f(y + 4.2)} {f(x + 3.6)},{f(y)}", "#c3fff4", 1.6, alpha=0.8)
    gloss(a, 48, 44, 11, 5)
    a.add(blob([(62, 72), (54, 80), (52, 88), (62, 86), (70, 76)]), fill=yellow, stroke=ink, width=2.2)
    a.add(blob([(98, 56), (108, 58), (110, 64), (104, 70), (98, 67)]), fill="#fff7e6", stroke=ink, width=2.2)
    a.line("M100,63 Q104,66 108.5,63.5", ink, 1.6)
    big_eye(a, 86, 54, 7.2)
    blush(a, 90, 67, 4.4, 2.6)
    return a


# ---- Wanderers ----------------------------------------------------------------------------------------


def turtle():
    a = Art("turtle", W, W, "creature")
    ink = "#3b3514"
    skin = vgrad(40, 104, "#d9e39a", "#95a64e")
    for pts, w0, w1 in (([(40, 80), (30, 92), (22, 98)], 13, 6), ([(84, 80), (92, 94), (98, 100)], 14, 7), ([(24, 70), (14, 72)], 7, 3)):
        a.add(taper(pts, w0, w1), fill=skin, stroke=ink, width=2.6)
    a.add(blob([(28, 76), (34, 82), (60, 86), (86, 82), (94, 76), (60, 78)]), fill="#e6dca0", stroke=ink, width=2.4)
    shell = blob([(22, 74), (24, 56), (36, 40), (56, 32), (76, 34), (92, 46), (98, 62), (96, 74), (60, 78)])
    a.add(shell, fill=rad(52, 40, 60, (0, "#d8e28a"), (0.55, "#8fb04c"), (1, "#5c7a2c")), stroke=ink, width=3)
    for cx, cy, r in ((60, 52, 9), (43, 58, 7), (77, 58, 7), (52, 69, 5.5), (68, 69, 5.5), (32, 69, 4.5), (88, 68, 4.5)):
        a.add(poly([arc_pt(cx, cy, r, -90 + k * 60) for k in range(6)]), fill="#c4d86a", stroke="#5c7a2c", width=1.6)
    a.add(blob([(22, 72), (60, 76), (98, 72), (97, 77), (60, 81), (23, 77)]), fill="#7c9a38", alpha=0.8)
    gloss(a, 44, 42, 10, 5, 0.45)
    a.add(circle(100, 58, 15), fill=rad(96, 52, 20, (0, "#eef5b8"), (1, "#a9bb5c")), stroke=ink, width=3)
    face(a, 101, 55, 11, 4.6, ink, mouth=3.4, mouth_dy=7.5)
    return a


def snake():
    a = Art("snake", W, W, "creature")
    ink = "#15324a"
    band = "#2a5b85"
    light = "#a6e2f7"
    c1, c2, r = (58, 46), (58, 88), 21
    body = f"M58,25 A21,21 0 0,0 58,67 A21,21 0 1,1 {f(58 + r * math.cos(math.radians(170)))},{f(88 + r * math.sin(math.radians(170)))}"
    a.add(blob([(34, 92), (31, 84), (32, 76), (38, 72), (41, 80), (40, 88)]), fill=light, stroke=ink, width=2.4)
    a.line(body, ink, 21)
    a.line(body, light, 15.5)
    for t in (-120, -150, -180, -210, -240):
        a0, a1 = math.radians(t - 7), math.radians(t + 7)
        p0 = (c1[0] + r * math.cos(a0), c1[1] + r * math.sin(a0))
        p1 = (c1[0] + r * math.cos(a1), c1[1] + r * math.sin(a1))
        a.line(f"M{pt(p0)} A21,21 0 0,1 {pt(p1)}", band, 15.5, cap="butt")
    for t in (-60, -30, 0, 30, 60, 90, 120, 150):
        a0, a1 = math.radians(t - 7), math.radians(t + 7)
        p0 = (c2[0] + r * math.cos(a0), c2[1] + r * math.sin(a0))
        p1 = (c2[0] + r * math.cos(a1), c2[1] + r * math.sin(a1))
        a.line(f"M{pt(p0)} A21,21 0 0,1 {pt(p1)}", band, 15.5, cap="butt")
    a.line(f"M{pt(arc_pt(58, 46, 18, -110))} A18,18 0 0,0 {pt(arc_pt(58, 46, 18, -200))}", "#ffffff", 2.6, alpha=0.4)
    a.line(f"M{pt(arc_pt(58, 88, 18, -50))} A18,18 0 0,1 {pt(arc_pt(58, 88, 18, 20))}", "#ffffff", 2.6, alpha=0.4)
    # A big round head with a little tongue.
    a.line("M88,28 Q93,29 95,26 M92,28.5 Q95,31 97,32", "#ff5a78", 2)
    a.add(ellipse(68, 22, 21, 16), fill=rad(62, 14, 28, (0, "#d6f4ff"), (1, "#6cc3e4")), stroke=ink, width=3)
    a.add(blob([(50, 16), (58, 8), (70, 7), (80, 10), (70, 12), (58, 14)]), fill=band, alpha=0.85)
    face(a, 71, 22, 15, 5.4, ink, mouth=3.6, mouth_dy=7)
    return a


def remora():
    a = Art("remora", W, W, "creature")
    ink = "#3a3024"
    a.add("M0,0 H120 V20 C96,33 44,36 0,26 Z", fill=vgrad(0, 34, "#8cb9cc", "#5f8ea3"))
    a.add("M0,26 C44,36 96,33 120,20 L120,26 C96,38 44,42 0,32 Z", fill="#f2f8fa", alpha=0.9)
    for x in (22, 46, 70, 94):
        a.line(smooth([(x, 27), (x + 3, 31), (x + 1, 35)], closed=False), "#5f8ea3", 1.4, alpha=0.6)
    body = vgrad(46, 84, "#fff0dc", "#d6b894")
    a.add(blob([(26, 64), (14, 54), (8, 50), (10, 62), (10, 66), (8, 80), (14, 76), (26, 70)]), fill="#f2c9a0", stroke=ink, width=2.6)
    a.add(blob([(42, 58), (52, 50), (64, 50), (70, 56)]), fill="#f2c9a0", stroke=ink, width=2)
    a.add(blob([(106, 68), (100, 54), (86, 48), (66, 48), (44, 52), (28, 60), (24, 67), (30, 74), (46, 80), (68, 84), (88, 82), (102, 76)]),
          fill=body, stroke=ink, width=3)
    a.add(taper([(28, 67), (50, 67), (74, 68), (92, 68)], 3.4, 2.4), fill="#8a6a4a", alpha=0.55)
    gloss(a, 50, 56, 12, 3.5, 0.5)
    a.add(ellipse(80, 47, 15, 5), fill="#fff3dc", stroke=ink, width=2.2)
    for x in range(70, 92, 4):
        a.line(f"M{x},44 L{x},50", "#b59f78", 1.4)
    big_eye(a, 88, 62, 6.4)
    blush(a, 92, 73, 4.2, 2.5)
    a.line("M98,73 Q102,76 106,72", ink, 1.8)
    return a


# ---- Traders, trappers and tricksters ------------------------------------------------------------------


def crab():
    a = Art("crab", W, W, "creature")
    ink = "#5a2a0c"
    leg = vgrad(78, 112, "#ffb37a", "#e0703a")
    for (p, q) in (((52, 88), (40, 104)), ((60, 92), (54, 108)), ((68, 94), (68, 110))):
        a.add(taper([p, q], 7, 5), fill=leg, stroke=ink, width=2.2)
    shell = rad(80, 44, 54, (0, "#fff6e6"), (0.5, "#ffd39e"), (1, "#d99354"))
    a.add(blob([(54, 88), (50, 70), (56, 48), (72, 32), (92, 28), (108, 40), (112, 60), (104, 78), (88, 90), (70, 94)]), fill=shell, stroke=ink, width=3)
    spiral = []
    for i in range(0, 64):
        t = i / 63
        ang = math.radians(-100 + t * 560)
        r = 25 * (1 - t) + 3
        spiral.append((86 + r * math.cos(ang), 58 + r * math.sin(ang) * 0.9))
    a.line(smooth(spiral[::3], closed=False), "#c27a3c", 2.8)
    gloss(a, 72, 42, 10, 5, 0.6)
    a.add(circle(98, 40, 2.2), fill="#ffffff", alpha=0.8)
    # The crab peeking out: a round face, eyes on stalks, chunky claws.
    a.add(ellipse(46, 78, 16, 13), fill=vgrad(64, 92, "#ffb37a", "#ec7c44"), stroke=ink, width=2.6)
    for p, q in (((40, 68), (34, 52)), ((52, 68), (54, 51))):
        a.add(taper([p, q], 4, 3.4), fill=leg, stroke=ink, width=1.8)
    for x, y in ((34, 48), (54, 47)):
        a.add(circle(x, y, 7.4), fill="#ffffff", stroke=ink, width=2.2)
        big_eye(a, x + 1, y + 0.5, 4.6)
    blush(a, 36, 82, 3.8, 2.3)
    blush(a, 57, 82, 3.8, 2.3)
    smile(a, 46.5, 82, 3.6, ink, 2)
    claw = vgrad(62, 96, "#ffbf8a", "#e0703a")
    a.add(blob([(34, 84), (22, 78), (12, 80), (10, 90), (18, 98), (30, 96), (36, 90)]), fill=claw, stroke=ink, width=2.6)
    a.add(blob([(14, 82), (6, 74), (4, 80), (8, 86)]), fill=claw, stroke=ink, width=2.2)
    gloss(a, 20, 84, 4, 2.2, 0.6)
    return a


def angler():
    a = Art("angler", W, W, "creature")
    ink = "#1c1d4a"
    a.add(circle(96, 18, 17), fill=rad(96, 18, 17, (0, "#fff7b8", 0.95), (0.4, "#ffe066", 0.45), (1, "#ffe066", 0)))
    a.add(taper([(62, 30), (66, 16), (76, 8), (88, 8), (95, 14)], 3.4, 2.6), fill="#6d74d6", stroke=ink, width=1.4)
    a.add(blob([(26, 58), (14, 48), (8, 46), (10, 60), (8, 74), (14, 72), (26, 66)]), fill="#5058b8", stroke=ink, width=2.6)
    a.add(blob([(98, 62), (96, 44), (84, 32), (64, 28), (44, 32), (30, 44), (24, 60), (28, 76), (42, 90), (62, 96), (82, 92), (94, 80)]),
          fill=rad(52, 44, 62, (0, "#9aa2ff"), (0.55, "#5d66d0"), (1, "#343a92")), stroke=ink, width=3)
    for x, y, r in ((40, 44, 2), (34, 60, 1.6), (48, 72, 1.8), (58, 38, 1.4)):
        a.add(circle(x, y, r), fill="#d8dcff", alpha=0.5)
    gloss(a, 44, 42, 10, 5.5, 0.45)
    # A wide grin with small round teeth.
    a.add("M60,70 Q78,90 97,66 Q80,78 60,70 Z", fill="#7a2a4a", stroke=ink, width=2.4)
    for i in range(5):
        t = (i + 0.5) / 5
        x = 62 + 33 * t
        y = 70 + 7 * math.sin(math.pi * t) + (-3 if t > 0.8 else 0)
        a.add(poly([(x - 2, y - 0.6), (x + 2, y - 0.6), (x, y + 3.2)]), fill="#ffffff", stroke=ink, width=0.8)
    big_eye(a, 74, 50, 8)
    blush(a, 60, 62, 4.8, 2.8)
    blush(a, 90, 58, 4, 2.4)
    a.add(blob([(50, 82), (42, 90), (40, 96), (50, 92), (56, 86)]), fill="#474fb0", stroke=ink, width=2.2)
    a.add(circle(96, 18, 6.4), fill=rad(95, 16, 7, (0, "#ffffff"), (0.6, "#fff4a8"), (1, "#ffcf3a")), stroke="#a8861c", width=1.4)
    for x, y in ((106, 8), (110, 22), (84, 6)):
        a.add(poly([arc_pt(x, y, 3 if k % 2 == 0 else 1.1, -90 + k * 45) for k in range(8)]), fill="#fff7c0")
    return a


def octopus():
    a = Art("octopus", W, W, "creature")
    ink = "#6a1a2a"
    arm = vgrad(56, 112, "#ff9aaa", "#e05570")
    arms = [
        [(38, 64), (24, 72), (16, 84), (20, 94), (28, 94), (28, 86)],
        [(46, 70), (40, 86), (40, 100), (48, 106), (54, 100)],
        [(56, 72), (56, 90), (62, 104), (70, 106), (72, 98)],
        [(66, 72), (72, 88), (82, 98), (92, 98), (92, 90)],
        [(76, 68), (90, 74), (100, 86), (108, 84), (106, 76)],
        [(82, 60), (96, 60), (104, 52), (102, 44), (96, 46)],
    ]
    for pts in arms:
        a.add(taper(pts, 13, 5), fill=arm, stroke=ink, width=2.4)
        samples = spline_points(pts, 6)
        for i in range(3, int(len(samples) * 0.7), 4):
            x, y = samples[i]
            a.add(circle(x, y + 2.5, 1.7), fill="#ffe0e6", alpha=0.9)
    a.add(blob([(60, 12), (80, 16), (92, 32), (92, 50), (82, 64), (60, 70), (38, 64), (28, 50), (28, 32), (40, 16)]),
          fill=rad(52, 28, 60, (0, "#ffc2cc"), (0.5, "#ff7e94"), (1, "#d24a68")), stroke=ink, width=3)
    for x, y, r in ((74, 26, 2.6), (82, 38, 2), (46, 24, 1.8)):
        a.add(circle(x, y, r), fill="#ffe0e6", alpha=0.7)
    gloss(a, 44, 28, 10, 6, 0.5)
    face(a, 60, 46, 22, 7.2, ink, mouth=5.5)
    return a


def cuttlefish():
    a = Art("cuttlefish", W, W, "creature")
    ink = "#4a1a3c"
    skirt = []
    top = ring_points(58, 62, 46, 27, 185, 355, 14)
    bottom = ring_points(58, 62, 46, 27, 5, 175, 14)
    for i, p in enumerate(top + bottom):
        k = 1.0 if i % 2 == 0 else 0.9
        skirt.append((58 + (p[0] - 58) * k, 62 + (p[1] - 62) * k))
    a.add(blob(skirt), fill=lin(12, 0, 104, 0, (0, "#ffc6e6"), (0.5, "#fff0b8"), (1, "#b8f0f2")), stroke=ink, width=2.2, alpha=0.95)
    for pts in ([(100, 60), (108, 54), (113, 50)], [(101, 63), (110, 62), (115, 61)], [(101, 66), (110, 69), (114, 72)]):
        a.add(taper(pts, 5, 2.6), fill="#ffd27a", stroke=ink, width=1.6)
    a.add(blob([(104, 62), (98, 48), (80, 40), (56, 40), (34, 46), (22, 58), (22, 68), (34, 78), (56, 84), (80, 84), (98, 76)]),
          fill=lin(22, 0, 104, 0, (0, "#ff8cc6"), (0.5, "#ffd66b"), (1, "#5fd6de")), stroke=ink, width=3)
    for x in range(34, 72, 8):
        a.add(taper([(x + 2, 45), (x - 1, 54), (x + 2, 64), (x - 1, 74), (x + 1, 80)], 2.6, 1.8), fill="#ffffff", alpha=0.35)
    gloss(a, 50, 48, 13, 4.5, 0.5)
    big_eye(a, 86, 58, 7.2)
    blush(a, 88, 70, 4.4, 2.6)
    a.line("M94,70 Q98,73 102,69", ink, 1.8)
    return a


ALL = [shark, sardines, lionfish, starfish, coral, jellyfish, parrotfish, turtle, snake, remora, crab, angler, octopus, cuttlefish]

# Which portrait each faction key uses.
BY_FACTION = {
    "sharks": "shark", "sardines": "sardines", "lionfish": "lionfish", "starfish": "starfish", "coral": "coral",
    "jellyfish": "jellyfish", "parrotfish": "parrotfish", "turtles": "turtle", "snake": "snake", "remoras": "remora",
    "crabs": "crab", "anglers": "angler", "octopus": "octopus", "cuttlefish": "cuttlefish",
}
