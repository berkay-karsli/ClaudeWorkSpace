"""Suits, pieces and tokens, action icons and map scenery."""
import math

from artlib import (Art, blob, taper, circle, ellipse, poly, smooth, rrect, lin, rad, vgrad, lighten, darken, mix,
                    arc_pt, ring_points, star_points, lerp, f, pt)
from creatures import eye, shine

INK = "#10222b"
CREAM = "#f6f0e2"
SUIT = {"kelp": "#86bf5e", "sponge": "#f0b95a", "pearl": "#9fb4ea", "moon": "#c9cfdb"}


def crescent(cx, cy, r, ox, oy, r2):
    """A crescent: the circle (cx, cy, r) minus the circle offset by (ox, oy) with radius r2."""
    d = math.hypot(ox, oy)
    a = (r * r - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(r * r - a * a, 0))
    ux, uy = ox / d, oy / d
    mx, my = cx + ux * a, cy + uy * a
    p1 = (mx - uy * h, my + ux * h)
    p2 = (mx + uy * h, my - ux * h)
    return f"M{pt(p1)} A{f(r)},{f(r)} 0 1,1 {pt(p2)} A{f(r2)},{f(r2)} 0 1,0 {pt(p1)} Z"


# ---- Suits ------------------------------------------------------------------------------------------


def suit_kelp():
    a = Art("suit_kelp", 48, 48, "suit")
    g = vgrad(4, 46, "#b6e38a", "#3f7f2a")
    for pts, w0 in (([(18, 46), (15, 34), (19, 22), (14, 11), (17, 4)], 5.5), ([(28, 46), (31, 35), (27, 24), (33, 13), (31, 6)], 5)):
        a.add(taper(pts, w0, 2.4), fill=g, stroke="#1f4a17", width=1.6)
    for base, tip in (((17, 30), (8, 24)), ((18, 20), (26, 13)), ((29, 30), (39, 26)), ((29, 19), (21, 14)), ((16, 12), (9, 7))):
        a.add(taper([base, lerp(base, tip, 0.5), tip], 4.5, 1.2), fill=g, stroke="#1f4a17", width=1.2)
    for x, y in ((17.5, 30), (29, 30), (18, 20), (29, 19)):
        a.add(circle(x, y, 2), fill="#d6f0a8", stroke="#1f4a17", width=1)
    return a


def suit_sponge():
    a = Art("suit_sponge", 48, 48, "suit")
    ink = "#6a3d08"
    g = vgrad(6, 46, "#ffd98a", "#c77b18")
    for x, y, w, h in ((6, 20, 12, 26), (30, 16, 12, 30), (17, 7, 14, 39)):
        a.add(rrect(x, y, w, h, 5), fill=g, stroke=ink, width=1.8)
        a.add(ellipse(x + w / 2, y + 3.5, w / 2 - 2, 2.4), fill="#7a4a0e")
        for i in range(3):
            a.add(circle(x + w / 2 + (i - 1) * 2.8, y + 12 + i * 7, 1.2), fill="#a8661a", alpha=0.8)
    a.add(taper([(20, 14), (20, 26)], 2, 1), fill="#ffffff", alpha=0.45)
    return a


def suit_pearl():
    a = Art("suit_pearl", 48, 48, "suit")
    ink = "#2f2f5a"
    shell = vgrad(6, 46, "#dfe5fb", "#7f8fc9")
    a.add(blob([(4, 30), (12, 24), (24, 22), (36, 24), (44, 30), (38, 40), (24, 44), (10, 40)]), fill=shell, stroke=ink, width=1.8)
    for x in (12, 18, 24, 30, 36):
        a.line(smooth([(x, 26), (24 + (x - 24) * 0.7, 36), (24 + (x - 24) * 0.3, 43)], closed=False), "#6b78b8", 1, alpha=0.8)
    a.add(blob([(6, 26), (10, 14), (24, 6), (38, 12), (43, 24), (36, 21), (24, 19), (12, 21)]), fill=vgrad(6, 26, "#f2f4ff", "#9aa7dc"), stroke=ink, width=1.8)
    a.add(circle(24, 27, 7), fill=rad(21.5, 24.5, 9, (0, "#ffffff"), (0.6, "#efe8ff"), (1, "#b7b0e0")), stroke=ink, width=1.2)
    a.add(circle(21.5, 24.5, 1.8), fill="#ffffff")
    return a


def suit_moon():
    a = Art("suit_moon", 48, 48, "suit")
    a.add(crescent(22, 25, 17, 9, -6, 15), fill=rad(14, 22, 22, (0, "#ffffff"), (1, "#a8b2c8")), stroke="#39445c", width=1.8)
    for (x, y, r) in ((36, 10, 4), (41, 25, 2.6), (32, 38, 2)):
        a.add(poly(star_points(x, y, 4, r, r * 0.35)), fill="#fff6c8", stroke="#6d6a3a", width=0.6)
    return a


# ---- Pieces and tokens --------------------------------------------------------------------------------


def piece_coral():
    a = Art("piece_coral", 48, 48, "piece")
    ink = "#1c2229"
    a.add(ellipse(24, 43, 15, 3.6), fill=ink)
    parts = [taper(p, w0, w1) for p, w0, w1 in (
        ([(24, 43), (23, 33), (22, 24)], 7.5, 6), ([(23, 33), (15, 27), (12, 16)], 5.5, 4), ([(22, 27), (27, 18), (27, 8)], 5.5, 4),
        ([(24, 37), (32, 31), (36, 20)], 5.5, 4))]
    for d in parts:
        a.add(d, fill=ink, stroke=ink, width=3.5)
    for d in parts:
        a.add(d, fill="#e0606a")
    for x, y in ((12, 15), (27, 7), (36, 19)):
        a.add(circle(x, y, 2.2), fill="#f3e7cc", stroke=ink, width=1)
    return a


def piece_market():
    a = Art("piece_market", 48, 48, "piece")
    ink = "#4a2208"
    a.add(blob([(8, 42), (6, 30), (12, 16), (24, 7), (36, 12), (42, 26), (40, 42)]), fill=rad(22, 18, 30, (0, "#fff0d6"), (0.6, "#e2b074"), (1, "#9c6630")), stroke=ink, width=2)
    spiral = [arc_pt(26, 22, 11 - i * 0.45, -60 + i * 38) for i in range(18)]
    a.line(smooth(spiral, closed=False), "#8a5526", 1.6)
    a.add("M16,42 V33 A6,6 0 0,1 28,33 V42 Z", fill="#3a1d08", stroke=ink, width=1.4)
    a.add(poly([(35, 6), (35, 16)], closed=False), stroke=ink, width=1.4)
    a.add(poly([(35.5, 6), (44, 8.5), (35.5, 11)]), fill="#e3534a", stroke=ink, width=1)
    return a


def piece_nest():
    a = Art("piece_nest", 48, 48, "piece")
    a.add(blob([(3, 40), (8, 30), (24, 26), (40, 30), (45, 40), (24, 45)]), fill=vgrad(26, 46, "#f3dfb0", "#b99555"), stroke="#6a5226", width=1.6)
    for x, y in ((16, 30), (25, 27.5), (33, 30.5)):
        a.add(ellipse(x, y, 5, 5.8), fill=rad(x - 1.5, y - 2, 7, (0, "#ffffff"), (1, "#d9d2c0")), stroke="#6a5226", width=1.2)
    a.add(blob([(4, 40), (12, 36), (24, 35), (36, 36), (44, 40), (24, 44)]), fill="#d7bb7a")
    for x in (10, 18, 30, 38):
        a.add(circle(x, 40, 0.9), fill="#8a6c34", alpha=0.7)
    return a


def piece_egg():
    a = Art("piece_egg", 48, 48, "piece")
    a.add(ellipse(24, 26, 12, 14.5), fill=rad(19, 20, 18, (0, "#ffffff"), (1, "#d6ceb8")), stroke="#6a5226", width=1.8)
    a.add(ellipse(19.5, 20, 3, 4.5), fill="#ffffff", alpha=0.8)
    return a


def piece_blood():
    a = Art("piece_blood", 48, 48, "piece")
    a.add(blob([(24, 4), (32, 16), (38, 27), (36, 38), (24, 44), (12, 38), (10, 27), (16, 16)]),
          fill=rad(20, 22, 26, (0, "#ff6b6b"), (0.6, "#c81e2c"), (1, "#7a0c16")), stroke="#4a0710", width=1.8)
    a.add(taper([(17, 30), (16, 24), (20, 16)], 3, 1), fill="#ffffff", alpha=0.5)
    for x, y, r in ((40, 12, 2.4), (7, 16, 1.8), (41, 40, 1.6)):
        a.add(circle(x, y, r), fill="#c81e2c", alpha=0.7)
    return a


def piece_rubble():
    a = Art("piece_rubble", 48, 48, "piece")
    ink = "#4a4640"
    for pts in ([(6, 40), (10, 30), (20, 28), (22, 40)], [(18, 42), (22, 30), (32, 26), (38, 34), (34, 43)],
                [(28, 26), (26, 16), (34, 10), (40, 16), (38, 28)], [(8, 30), (12, 20), (20, 22), (18, 30)]):
        a.add(poly(pts), fill=vgrad(8, 44, "#f1ece2", "#a59c8e"), stroke=ink, width=1.6)
    for x, y in ((14, 36), (28, 36), (33, 18), (14, 26)):
        a.add(circle(x, y, 1.2), fill="#8a8274", alpha=0.8)
    return a


def lure(name, glow, symbol):
    a = Art(name, 48, 48, "piece")
    a.add(circle(28, 20, 18), fill=rad(28, 20, 18, (0, lighten(glow, 0.6), 0.9), (0.45, glow, 0.35), (1, glow, 0)))
    a.add(taper([(6, 46), (8, 30), (16, 16), (24, 14)], 3, 2), fill="#3a3f8a", stroke="#0f1030", width=1)
    a.add(circle(28, 20, 8), fill=rad(26, 18, 10, (0, "#ffffff"), (0.55, lighten(glow, 0.45)), (1, glow)), stroke=darken(glow, 0.45), width=1.4)
    symbol(a)
    return a


def lure_treasure():
    def coin(a):
        a.add(circle(28, 20, 4), fill="#f7cf3c", stroke="#7a5a08", width=1)
        a.line("M28,17.5 V22.5", "#7a5a08", 1)
    return lure("lure_treasure", "#f2c230", coin)


def lure_shelter():
    def dome(a):
        a.add("M23.5,23 A4.5,4.5 0 0,1 32.5,23 Z", fill="#e8fbff", stroke="#0e5360", width=1)
    return lure("lure_shelter", "#3fc7d6", dome)


def lure_glory():
    def star(a):
        a.add(poly(star_points(28, 20, 5, 4.6, 2)), fill="#fff3fb", stroke="#7a1a55", width=0.9)
    return lure("lure_glory", "#e05aa8", star)


def lure_dark():
    a = Art("lure_dark", 48, 48, "piece")
    a.add(taper([(6, 46), (8, 30), (16, 16), (24, 14)], 3, 2), fill="#3a3f8a", stroke="#0f1030", width=1)
    a.add(circle(28, 20, 8), fill=rad(26, 18, 10, (0, "#8a8f9e"), (1, "#3c4150")), stroke="#0f1030", width=1.4)
    a.line("M24,16 L32,24 M32,16 L24,24", "#0f1030", 1.4)
    return a


def piece_cyst():
    """What an immortal jellyfish shrinks into: a small bulb on a stalk, ready to grow back."""
    a = Art("piece_cyst", 48, 48, "piece")
    a.add(ellipse(24, 42, 12, 3), fill="#8d8173", stroke="#3a2a4a", width=1.2)
    a.add(taper([(24, 42), (23, 34), (24, 28)], 4, 3), fill="#b99ad8", stroke="#3a2a4a", width=1.2)
    a.add(blob([(24, 8), (33, 12), (36, 21), (32, 29), (24, 31), (16, 29), (12, 21), (15, 12)]),
          fill="#9a6cc8", stroke="#3a2a4a", width=1.6)
    for x in (18, 24, 30):
        a.add(circle(x, 21, 1.8), fill="#f3e7cc")
    a.add(taper([(18, 16), (21, 12), (26, 11)], 2.4, 1), fill="#ffffff", alpha=0.6)
    return a


def piece_cocoon():
    """A parrotfish asleep in its bubble of slime."""
    a = Art("piece_cocoon", 48, 48, "piece")
    a.add(ellipse(24, 26, 20, 16), fill=rad(20, 20, 22, (0, "#f4fffb", 0.9), (0.7, "#bfeee4", 0.75), (1, "#6fcfc0", 0.8)), stroke="#1f5a52", width=1.6)
    with a.group(tx=0, ty=0):
        a.add(blob([(33, 27), (27, 22), (17, 22.5), (12, 27), (17, 31.5), (27, 32)]), fill="#5fe0cc", stroke="#0e5348", width=1.3)
        a.add(blob([(12, 27), (7, 23), (8, 27), (7, 31)]), fill="#5fe0cc", stroke="#0e5348", width=1.1)
        a.line("M27,25.5 Q29,24.5 31,25.5", "#0e5348", 1.2)
    a.add(taper([(12, 17), (18, 13), (26, 12)], 3, 1), fill="#ffffff", alpha=0.7)
    return a


def pigment(name, color):
    a = Art(name, 48, 48, "piece")
    pts = []
    for i in range(16):
        r = 15 if i % 2 == 0 else 11.5
        r += (i * 7 % 5) * 0.6
        pts.append(arc_pt(24, 25, r, i * 22.5))
    a.add(blob(pts), fill=rad(20, 20, 20, (0, lighten(color, 0.45)), (1, darken(color, 0.15))), stroke=darken(color, 0.6), width=1.6)
    for (x, y, r) in ((41, 10, 2.4), (8, 40, 2), (42, 38, 1.5)):
        a.add(circle(x, y, r), fill=color, stroke=darken(color, 0.6), width=0.8)
    a.add(taper([(16, 26), (17, 19), (23, 15)], 3, 1), fill="#ffffff", alpha=0.55)
    return a


def turban(a, ink):
    """A small spiral shell, as worn by the hermit crabs' customers."""
    a.add(blob([(30, 4), (37, 12), (42, 24), (39, 36), (28, 44), (14, 43), (6, 35), (9, 23), (19, 11)]),
          fill=rad(22, 16, 32, (0, "#fff4de"), (0.5, "#ebbd7e"), (1, "#a5692f")), stroke=ink, width=2)
    for pts in ([(12, 17), (24, 19), (36, 13)], [(8, 27), (24, 30), (41, 24)], [(8, 36), (22, 39), (40, 33)]):
        a.line(smooth(pts, closed=False), "#9a6532", 1.6)
    for pts in ([(18, 15), (26, 16.5)], [(15, 25), (28, 27)]):
        a.add(taper(pts, 2.4, 1), fill="#ffffff", alpha=0.55)
    a.add(ellipse(17, 38, 7, 4.6), fill="#5a3212", stroke=ink, width=1.4)
    a.add(ellipse(17, 37.4, 4.2, 2.4), fill="#2a1406")


def piece_shell():
    a = Art("piece_shell", 48, 48, "piece")
    turban(a, "#4a2208")
    return a


def piece_island():
    a = Art("piece_island", 48, 48, "piece")
    a.add(ellipse(24, 38, 21, 7), fill="#7fd3d9", alpha=0.35)
    a.add(blob([(5, 38), (12, 31), (24, 29), (37, 31), (43, 38), (24, 42)]), fill=vgrad(29, 42, "#fbe8b5", "#d2a95e"), stroke="#7a5a26", width=1.6)
    a.add(taper([(24, 33), (25, 22), (28, 12)], 3.2, 2), fill="#8a5a2a", stroke="#4a2e12", width=1)
    for tip in ((14, 12), (18, 6), (32, 5), (40, 12), (36, 17)):
        a.add(taper([(28, 12), lerp((28, 12), tip, 0.5), tip], 4.4, 1), fill=vgrad(4, 18, "#9fe07a", "#3f8f2a"), stroke="#1f4a17", width=1)
    return a


def piece_sandbar():
    a = Art("piece_sandbar", 64, 24, "piece")
    a.add(blob([(3, 14), (10, 7), (32, 5), (54, 7), (61, 14), (54, 19), (32, 20), (10, 19)]), fill=vgrad(5, 20, "#fbe8b5", "#caa25a"), stroke="#7a5a26", width=1.6)
    for x in (14, 26, 38, 50):
        a.line(smooth([(x - 4, 11), (x, 9), (x + 4, 11)], closed=False), "#b08a44", 1.2)
    return a


# ---- Action icons ---------------------------------------------------------------------------------------


def icon(name, draw):
    a = Art("icon_" + name, 48, 48, "icon")
    draw(a)
    return a


def arrowhead(a, tip, back, width, color, ink=INK):
    dx, dy = tip[0] - back[0], tip[1] - back[1]
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    p1 = (back[0] - uy * width / 2, back[1] + ux * width / 2)
    p2 = (back[0] + uy * width / 2, back[1] - ux * width / 2)
    a.add(poly([tip, p1, p2]), fill=color, stroke=ink, width=1.6)


def curved_arrow(a, pts, color, w0=5, w1=4.5, head=12):
    body = pts[:-1]
    a.add(taper(body, w0, w1), fill=color, stroke=INK, width=1.6)
    arrowhead(a, pts[-1], pts[-2], head, color)


def fish_shape(a, cx, cy, s, color, ink=INK):
    with a.group(tx=cx, ty=cy, sx=s):
        a.add(blob([(-9, 0), (-15, -6), (-14, 0), (-15, 6)]), fill=color, stroke=ink, width=1.3)
        a.add(blob([(12, 0), (4, -5.5), (-6, -5), (-11, 0), (-6, 5), (4, 5.5)]), fill=color, stroke=ink, width=1.5)
        a.add(circle(6.5, -1, 1.3), fill=ink)


def _move(a):
    curved_arrow(a, [(6, 38), (16, 24), (30, 16), (43, 12)], "#6fd6e2")


def _swim(a):
    fish_shape(a, 28, 24, 1.3, "#9fd8e6")
    for y, x0 in ((18, 2), (24, 0), (30, 2)):
        a.line(f"M{x0},{y} L{x0 + 7},{y}", CREAM, 2.2)


def _hunt(a):
    a.add(blob([(10, 34), (20, 12), (24, 6), (27, 16), (34, 34)]), fill=vgrad(6, 34, "#9fc0cf", "#46687a"), stroke=INK, width=1.8)
    a.add(smooth([(2, 36), (10, 33), (18, 37), (26, 33), (34, 37), (42, 33), (47, 35), (47, 44), (2, 44)]), fill="#3aa0c8", stroke=INK, width=1.6)
    arrowhead(a, (46, 18), (37, 18), 9, "#ff8c6b")
    a.line("M30,18 L38,18", "#ff8c6b", 3)


def _battle(a):
    a.add(poly(star_points(24, 24, 9, 21, 11, -80)), fill=rad(24, 24, 22, (0, "#ffe27a"), (0.6, "#ff9a4a"), (1, "#e2402f")), stroke=INK, width=1.8)
    a.add(poly([(17, 17), (22, 30), (25, 22), (31, 31)], closed=False), stroke=INK, width=2.4)


def _bite(a):
    a.add("M6,14 C14,6 34,6 42,14 L38,22 C30,16 18,16 10,22 Z", fill="#2a5578", stroke=INK, width=1.8)
    for x in (15, 33):
        a.add(poly([(x - 3, 19), (x + 3, 19), (x, 34)]), fill=CREAM, stroke=INK, width=1.4)
    for y in (38, 43):
        a.add(circle(24, y, 1.8), fill="#8fd3ee")


def _snap(a):
    a.add("M4,22 C8,8 40,8 44,22 Z", fill="#343a92", stroke=INK, width=1.8)
    a.add("M4,26 C8,40 40,40 44,26 Z", fill="#343a92", stroke=INK, width=1.8)
    for i in range(5):
        x = 10 + i * 7
        a.add(poly([(x - 2.2, 21), (x + 2.2, 21), (x, 26.5)]), fill=CREAM, stroke=INK, width=0.9)
        a.add(poly([(x + 1.3, 27), (x + 5.7, 27), (x + 3.5, 21.5)]), fill=CREAM, stroke=INK, width=0.9)
    a.add(circle(40, 8, 4), fill="#fff3a0", stroke="#a8861c", width=1)


def _arrive(a):
    a.add("M6,44 V20 A18,18 0 0,1 42,20 V44 H34 V21 A10,10 0 0,0 14,21 V44 Z", fill=vgrad(4, 44, "#bfb3a0", "#6e6454"), stroke=INK, width=1.8)
    a.add(circle(24, 32, 8.5), fill="#8be07a", stroke=INK, width=1.6)
    a.line("M24,27.5 V36.5 M19.5,32 H28.5", INK, 2.6)


def _spawn(a):
    for x, y, r in ((16, 30, 7), (29, 34, 6), (24, 20, 5.5), (35, 22, 4)):
        a.add(circle(x, y, r), fill=rad(x - 2, y - 2, r * 1.4, (0, "#ffffff"), (1, "#f4b6d0")), stroke=INK, width=1.4)
        a.add(circle(x + 0.5, y + 0.5, r * 0.35), fill="#e05a90", alpha=0.7)
    a.add(poly(star_points(40, 9, 4, 6, 2)), fill="#fff3a0", stroke=INK, width=1)


def _grow(a):
    a.add(ellipse(24, 42, 14, 3.5), fill="#8d8173", stroke=INK, width=1.4)
    for p, w0, w1 in (([(24, 42), (23, 30), (20, 18)], 6, 4), ([(23, 32), (32, 24), (34, 16)], 4.4, 3), ([(22, 26), (14, 20), (12, 12)], 4, 2.8)):
        a.add(taper(p, w0, w1), fill=vgrad(10, 42, "#ffa6ad", "#c9404d"), stroke=INK, width=1.4)
    arrowhead(a, (40, 4), (40, 12), 8, "#8be07a")
    a.line("M40,11 V20", "#8be07a", 3)


def _build(a):
    a.add(rrect(8, 22, 32, 20, 3), fill=vgrad(22, 42, "#f1d7a8", "#b88a4a"), stroke=INK, width=1.8)
    a.add(poly([(4, 24), (24, 8), (44, 24)]), fill="#e3534a", stroke=INK, width=1.8)
    a.add("M19,42 V33 A5,5 0 0,1 29,33 V42 Z", fill="#3a1d08", stroke=INK, width=1.4)


def _craft(a):
    a.add(poly(star_points(24, 24, 8, 19, 14.5, -90)), fill=vgrad(5, 43, "#ffe08a", "#c98a1c"), stroke=INK, width=1.8)
    a.add(circle(24, 24, 7.5), fill="#fff6d8", stroke=INK, width=1.6)
    a.add(circle(24, 24, 3.4), fill=rad(23, 23, 4, (0, "#ffffff"), (1, "#c9c2f0")), stroke=INK, width=1)


def _card(a):
    a.add(rrect(13, 6, 22, 32, 4), fill=vgrad(6, 38, "#fdf7ea", "#d9ccb0"), stroke=INK, width=1.8)
    a.add(poly(star_points(24, 18, 4, 6, 2.2)), fill="#9fb4ea", stroke=INK, width=1)
    arrowhead(a, (24, 46), (24, 38), 10, "#ff8c6b")


def _extra(a):
    a.add(poly([(28, 3), (10, 27), (22, 27), (18, 45), (38, 19), (26, 19)]), fill=vgrad(3, 45, "#fff3a0", "#f2b21c"), stroke=INK, width=1.8)


def _ambush(a):
    a.add(smooth([(4, 24), (14, 14), (24, 12), (34, 14), (44, 24), (34, 34), (24, 36), (14, 34)]), fill=CREAM, stroke=INK, width=1.8)
    a.add(circle(24, 24, 7), fill="#e2402f", stroke=INK, width=1.4)
    a.add(circle(24, 24, 3), fill=INK)
    for pts in ([(8, 46), (6, 36), (10, 28)], [(40, 46), (42, 36), (38, 28)], [(16, 46), (18, 38)]):
        a.add(taper(pts, 4.5, 2), fill="#6fb04a", stroke=INK, width=1.2)


def _dominance(a):
    a.add(poly([(6, 36), (6, 14), (15, 24), (24, 8), (33, 24), (42, 14), (42, 36)]), fill=vgrad(8, 36, "#ffe27a", "#d49a1c"), stroke=INK, width=1.8)
    a.add(rrect(6, 36, 36, 6, 2), fill="#d49a1c", stroke=INK, width=1.6)
    for x, c in ((15, "#e2402f"), (24, "#3fc7d6"), (33, "#8be07a")):
        a.add(circle(x, 30, 2.6), fill=c, stroke=INK, width=0.9)


def _endday(a):
    a.add("M6,30 A18,18 0 0,1 42,30 Z", fill=rad(24, 30, 18, (0, "#fff0a0"), (1, "#ff8c4a")), stroke=INK, width=1.8)
    for deg in (200, 235, 270, 305, 340):
        p, q = arc_pt(24, 30, 21, deg), arc_pt(24, 30, 26, deg)
        a.line(poly([p, q], closed=False), "#ffcf6a", 2.4)
    for y, c in ((34, "#3aa0c8"), (40, "#2a7fa8")):
        a.add(smooth([(2, y), (10, y - 2.5), (18, y + 0.5), (26, y - 2.5), (34, y + 0.5), (42, y - 2.5), (46, y), (46, y + 5), (2, y + 5)]), fill=c, stroke=INK, width=1.4)


def _done(a):
    a.add(circle(24, 24, 19), fill=vgrad(5, 43, "#9be88a", "#3f9a3a"), stroke=INK, width=1.8)
    a.line("M14,24 L21,31 L35,16", INK, 5.4)
    a.line("M14,24 L21,31 L35,16", "#ffffff", 2.6)


def _baitball(a):
    for i in range(9):
        ang = i * 40
        r = 8 + i * 1.6
        x, y = arc_pt(24, 24, r, ang)
        with a.group(rot=ang + 90, px=x, py=y):
            fish_shape(a, x, y, 0.42, "#c9d6e4")


def _gorge(a):
    a.add("M24,24 L44,12 A21,21 0 1,0 44,36 Z", fill=vgrad(3, 45, "#f08a5d", "#b8432a"), stroke=INK, width=1.8)
    for p in ((38, 16), (32, 19.5), (38, 32), (32, 28.5)):
        a.add(circle(p[0], p[1], 1.6), fill=CREAM)
    a.add(circle(18, 14, 2.6), fill=INK)
    fish_shape(a, 44, 24, 0.35, "#c9d6e4")


def _current(a):
    for y, c in ((14, "#6fd6e2"), (26, "#3aa0c8")):
        curved_arrow(a, [(4, y + 6), (14, y - 2), (26, y + 6), (36, y), (44, y + 2)], c, 4, 4, 9)
    a.add(smooth([(4, 40), (12, 36), (20, 41), (28, 36), (36, 41), (44, 37)], closed=False), stroke="#6fd6e2", width=2, alpha=0.6)


def _drift(a):
    a.add(smooth(ring_points(24, 22, 14, 13, 180, 360, 7) + [(38, 23), (31, 25), (24, 23), (17, 25), (10, 23)]), fill=vgrad(8, 26, "#e6d0ff", "#8656b4"), stroke=INK, width=1.6)
    for x in (14, 20, 26, 32):
        a.add(taper([(x, 25), (x - 2, 32), (x + 1, 39)], 2, 0.8), fill="#c6a2ee")
    arrowhead(a, (46, 42), (38, 38), 8, "#6fd6e2")
    a.line("M30,35 L39,39", "#6fd6e2", 2.6)


def _graze(a):
    a.add(blob([(8, 44), (6, 30), (12, 18), (22, 16), (24, 26), (30, 30), (34, 40)]), fill=vgrad(16, 44, "#ffa6ad", "#c9404d"), stroke=INK, width=1.8)
    a.add(blob([(26, 10), (38, 6), (46, 14), (44, 24), (34, 22)]), fill=vgrad(6, 24, "#5fe0cc", "#0e7568"), stroke=INK, width=1.6)
    a.add(poly([(28, 16), (34, 14), (32, 21)]), fill=CREAM, stroke=INK, width=1)
    for x, y in ((36, 32), (40, 36), (35, 40)):
        a.add(circle(x, y, 1.6), fill="#fbe8b5", stroke=INK, width=0.6)


def _feed(a):
    for pts in ([(16, 44), (12, 30), (16, 16), (12, 6)], [(26, 44), (28, 30), (24, 18), (28, 8)], [(36, 44), (38, 32), (34, 22)]):
        a.add(taper(pts, 5, 2), fill=vgrad(6, 44, "#b6e38a", "#3f7f2a"), stroke=INK, width=1.4)
    a.add(blob([(24, 30), (30, 26), (36, 30), (30, 34)]), fill="#c3c67a", stroke=INK, width=1.2)


def _lay(a):
    a.add(blob([(3, 40), (8, 32), (24, 28), (40, 32), (45, 40), (24, 45)]), fill=vgrad(28, 46, "#f3dfb0", "#b99555"), stroke="#6a5226", width=1.6)
    for x, y in ((17, 31), (27, 30)):
        a.add(ellipse(x, y, 5, 6), fill=rad(x - 1.5, y - 2, 7, (0, "#ffffff"), (1, "#d9d2c0")), stroke=INK, width=1.2)
    arrowhead(a, (36, 22), (36, 13), 9, "#8be07a")
    a.line("M36,5 V14", "#8be07a", 3)


def _attach(a):
    a.add(ellipse(24, 18, 17, 7), fill="#e5dccb", stroke=INK, width=1.8)
    for x in range(12, 38, 4):
        a.line(f"M{x},13.5 L{x},22.5", "#7e7462", 1.4)
    a.add(blob([(4, 36), (14, 30), (30, 29), (44, 34), (30, 40), (14, 40)]), fill=vgrad(29, 41, "#c2b8a4", "#6b6356"), stroke=INK, width=1.6)
    arrowhead(a, (24, 28), (24, 33), 7, "#ffcf6a")


def _letgo(a):
    a.add(ellipse(24, 12, 17, 6.5), fill="#e5dccb", stroke=INK, width=1.8)
    for x in range(12, 38, 4):
        a.line(f"M{x},8 L{x},16", "#7e7462", 1.4)
    fish_shape(a, 24, 38, 1.1, "#c2b8a4")
    arrowhead(a, (44, 30), (38, 26), 7, "#ffcf6a")
    a.line("M31,22 L39,27", "#ffcf6a", 2.6)


def _shell(a):
    turban(a, INK)


def _price(a):
    _shell(a)
    a.add(circle(34, 34, 9), fill=vgrad(25, 43, "#fff3a0", "#d49a1c"), stroke=INK, width=1.6)
    a.line("M34,29.5 V38.5", "#7a5a08", 2)


def _lure(a):
    a.add(circle(30, 18, 16), fill=rad(30, 18, 16, (0, "#fff6b0", 0.9), (0.45, "#f6d64a", 0.35), (1, "#f6d64a", 0)))
    a.add(taper([(6, 46), (8, 30), (16, 18), (26, 16)], 3.2, 2.2), fill="#4a4fae", stroke=INK, width=1)
    a.add(circle(30, 18, 7), fill=rad(28, 16, 9, (0, "#ffffff"), (0.6, "#fff3a0"), (1, "#f2cc3a")), stroke="#7a5a08", width=1.4)


def _steal(a):
    a.add(circle(32, 32, 7), fill=rad(30, 30, 9, (0, "#ffffff"), (0.6, "#efe8ff"), (1, "#b7b0e0")), stroke=INK, width=1.4)
    a.add(taper([(4, 8), (16, 12), (26, 20), (38, 26), (42, 36), (36, 42), (30, 40)], 8, 2.4), fill=vgrad(4, 44, "#e06a7b", "#8e2233"), stroke=INK, width=1.6)
    for x, y in ((12, 11.5), (19, 15), (26, 20.5)):
        a.add(circle(x, y + 2.4, 1.2), fill="#fbd2da")


def _recoil(a):
    a.add(taper([(40, 40), (40, 24), (32, 12), (20, 10), (10, 16), (8, 26)], 7, 3), fill=vgrad(8, 42, "#e06a7b", "#8e2233"), stroke=INK, width=1.6)
    arrowhead(a, (9, 37), (8, 27), 11, "#ff8c6b")


def _order(a):
    a.add(rrect(6, 8, 22, 30, 4), fill=vgrad(8, 38, "#fdf7ea", "#d9ccb0"), stroke=INK, width=1.8)
    a.line("M14,16 L18,13 V31", INK, 2.8)
    a.add(taper([(44, 44), (40, 32), (32, 26), (24, 28)], 7, 2.6), fill=vgrad(20, 44, "#e06a7b", "#8e2233"), stroke=INK, width=1.6)


def _reach(a):
    a.add(taper([(6, 42), (14, 32), (24, 28), (34, 22)], 8, 3.2), fill=vgrad(20, 44, "#e06a7b", "#8e2233"), stroke=INK, width=1.6)
    arrowhead(a, (46, 12), (38, 18), 10, "#6fd6e2")


def _grab(a):
    a.add(poly(star_points(30, 20, 8, 14, 8, -80)), fill=rad(30, 20, 14, (0, "#ffe27a"), (1, "#ff9a4a")), stroke=INK, width=1.4)
    a.add(taper([(4, 44), (14, 34), (22, 24), (30, 20), (36, 24), (34, 30), (28, 30)], 8, 2.4), fill=vgrad(18, 44, "#e06a7b", "#8e2233"), stroke=INK, width=1.6)


def _mantle(a):
    a.add(blob([(24, 6), (34, 9), (40, 18), (38, 28), (30, 34), (24, 35), (18, 34), (10, 28), (8, 18), (14, 9)]),
          fill=rad(20, 14, 28, (0, "#f08a98"), (1, "#8e2233")), stroke=INK, width=1.8)
    for x in (18, 30):
        a.add(ellipse(x, 26, 3.4, 2.8), fill="#fff4d6", stroke=INK, width=1)
        a.add(ellipse(x, 26.3, 2.2, 0.9), fill=INK)
    for x in (10, 18, 30, 38):
        a.line(smooth([(x, 34), (x + (x - 24) * 0.3, 40), (x, 46)], closed=False), "#c23a4e", 3)


def _paint(a):
    for (x, y, c) in ((16, 30, "#86bf5e"), (32, 30, "#f0b95a"), (24, 17, "#9fb4ea")):
        a.add(circle(x, y, 10), fill=c, stroke=INK, width=1.6, alpha=0.92)
    a.add(taper([(40, 4), (32, 14), (26, 22)], 4, 2), fill="#f5d36b", stroke=INK, width=1.2)


def _dig(a):
    a.add(smooth([(2, 38), (10, 30), (22, 28), (34, 30), (46, 38), (46, 46), (2, 46)]), fill=vgrad(28, 46, "#fbe8b5", "#caa25a"), stroke=INK, width=1.6)
    a.add(taper([(38, 4), (30, 16), (24, 26)], 3.4, 3), fill="#a0724a", stroke=INK, width=1.2)
    a.add(blob([(18, 24), (26, 21), (30, 28), (24, 36), (16, 32)]), fill=vgrad(20, 36, "#e8eef4", "#8fa0b3"), stroke=INK, width=1.6)


def _leave(a):
    a.add("M4,44 V16 A12,12 0 0,1 28,16 V44 Z", fill="#0d2a3a", stroke=INK, width=1.8)
    a.add("M4,44 V16 A12,12 0 0,1 28,16 V44 H22 V17 A6,6 0 0,0 10,17 V44 Z", fill=vgrad(4, 44, "#bfb3a0", "#6e6454"), stroke=INK, width=1.4)
    curved_arrow(a, [(14, 32), (26, 30), (36, 28), (46, 28)], "#ffcf6a", 4.5, 4, 10)


def _sandbar(a):
    a.add(blob([(2, 30), (10, 22), (24, 20), (38, 22), (46, 30), (38, 36), (24, 38), (10, 36)]), fill=vgrad(20, 38, "#fbe8b5", "#caa25a"), stroke=INK, width=1.6)
    for x in (12, 24, 36):
        a.line(smooth([(x - 4, 28), (x, 26), (x + 4, 28)], closed=False), "#b08a44", 1.3)
    a.line("M24,4 V14 M19,9 H29", "#8be07a", 3)


def _island(a):
    a.add(blob([(4, 40), (12, 32), (24, 30), (37, 32), (44, 40), (24, 44)]), fill=vgrad(30, 44, "#fbe8b5", "#d2a95e"), stroke=INK, width=1.6)
    a.add(taper([(24, 34), (25, 22), (28, 12)], 3.2, 2), fill="#8a5a2a", stroke=INK, width=1)
    for tip in ((14, 12), (18, 5), (32, 4), (40, 12), (36, 17)):
        a.add(taper([(28, 12), lerp((28, 12), tip, 0.5), tip], 4.4, 1), fill=vgrad(4, 18, "#9fe07a", "#3f8f2a"), stroke=INK, width=1)


def _slither(a):
    a.add(taper([(6, 40), (16, 34), (14, 24), (24, 16), (34, 20), (38, 12)], 7, 5), fill="#8fd3ee", stroke=INK, width=1.8)
    a.add(ellipse(40, 9, 5.5, 4), fill="#2a5578", stroke=INK, width=1.4)
    for x, y in ((12, 36.5), (15, 28), (22, 18), (32, 18.5)):
        a.add(circle(x, y, 2.3), fill="#163a57")


def _setup(a):
    a.add(taper([(14, 44), (14, 6)], 3, 3), fill="#8a6a4a", stroke=INK, width=1.2)
    a.add(smooth([(15, 6), (26, 4), (36, 10), (44, 8), (40, 18), (30, 22), (15, 20)]), fill=vgrad(4, 22, "#ff9a7a", "#e2402f"), stroke=INK, width=1.6)
    a.add(ellipse(14, 44, 10, 2.5), fill="#8d8173", stroke=INK, width=1.2)


def _nest(a):
    a.add(blob([(3, 40), (8, 30), (24, 26), (40, 30), (45, 40), (24, 45)]), fill=vgrad(26, 46, "#f3dfb0", "#b99555"), stroke=INK, width=1.6)
    for x, y in ((16, 30), (25, 27.5), (33, 30.5)):
        a.add(ellipse(x, y, 5, 5.8), fill=rad(x - 1.5, y - 2, 7, (0, "#ffffff"), (1, "#d9d2c0")), stroke=INK, width=1.2)
    a.line("M24,4 V16 M18,10 H30", "#8be07a", 3)


def _market(a):
    a.add(blob([(8, 42), (6, 30), (12, 16), (24, 7), (36, 12), (42, 26), (40, 42)]), fill=rad(22, 18, 30, (0, "#fff0d6"), (0.6, "#e2b074"), (1, "#9c6630")), stroke=INK, width=2)
    a.add("M16,42 V33 A6,6 0 0,1 28,33 V42 Z", fill="#3a1d08", stroke=INK, width=1.4)
    a.add(poly([(35, 6), (35, 16)], closed=False), stroke=INK, width=1.4)
    a.add(poly([(35.5, 6), (44, 8.5), (35.5, 11)]), fill="#e3534a", stroke=INK, width=1)


def _recruit(a):
    fish_shape(a, 20, 28, 1.0, "#f2a064")
    a.add(circle(34, 16, 9), fill="#8be07a", stroke=INK, width=1.6)
    a.line("M34,11.5 V20.5 M29.5,16 H38.5", INK, 2.6)


def _feedblood(a):
    a.add(blob([(16, 6), (22, 16), (26, 25), (24, 34), (16, 38), (8, 34), (6, 25), (10, 16)]),
          fill=rad(13, 20, 18, (0, "#ff6b6b"), (0.6, "#c81e2c"), (1, "#7a0c16")), stroke=INK, width=1.6)
    a.add("M26,40 C30,28 40,24 46,26 L44,34 C38,32 32,36 30,42 Z", fill="#9fc0cf", stroke=INK, width=1.6)
    for x, y in ((33, 33), (38, 30.5), (43, 29.5)):
        a.add(poly([(x - 2, y), (x + 2, y), (x, y + 4)]), fill=CREAM, stroke=INK, width=0.8)


def _frenzy(a):
    a.add(circle(24, 24, 6), fill=rad(22, 22, 7, (0, "#ff6b6b"), (1, "#a81426")), stroke=INK, width=1.4)
    for deg in (0, 120, 240):
        x, y = arc_pt(24, 24, 15, deg)
        with a.group(rot=deg + 90, px=x, py=y):
            a.add(blob([(x - 6, y + 3), (x - 1, y - 7), (x + 1, y - 7), (x + 6, y + 3)]), fill=vgrad(y - 7, y + 3, "#9fc0cf", "#46687a"), stroke=INK, width=1.4)
    a.add(smooth(ring_points(24, 24, 20, 20, 0, 300, 9), closed=False), stroke="#6fd6e2", width=2.2, alpha=0.8)


def _rally(a):
    for x, y, s in ((12, 14, 0.7), (12, 34, 0.7), (22, 24, 0.9)):
        fish_shape(a, x, y, s, "#c9d6e4")
    a.add(circle(38, 24, 8.5), fill="#8be07a", stroke=INK, width=1.6)
    a.line("M38,19.5 V28.5 M33.5,24 H42.5", INK, 2.6)


def _push(a):
    for y in (10, 22, 34):
        fish_shape(a, 12, y, 0.7, "#c9d6e4")
    curved_arrow(a, [(20, 22), (28, 22), (36, 22)], "#ffcf6a", 5, 5, 11)
    fish_shape(a, 42, 36, 0.45, "#f2a064")


def _release(a):
    a.add("M4,44 V20 A14,14 0 0,1 32,20 V44 H26 V21 A8,8 0 0,0 10,21 V44 Z", fill=vgrad(4, 44, "#bfb3a0", "#6e6454"), stroke=INK, width=1.6)
    fish_shape(a, 34, 30, 0.8, "#f08a5d")
    for x in (28, 33, 38):
        a.line(f"M{x},22 L{x - 3},14", "#b8432a", 1.6)


def _breed(a):
    fish_shape(a, 16, 18, 0.9, "#f08a5d")
    fish_shape(a, 34, 34, 0.5, "#f08a5d")
    curved_arrow(a, [(18, 28), (22, 34), (26, 36)], "#8be07a", 3, 3, 7)


def _devour(a):
    a.add(rrect(26, 26, 16, 16, 3), fill=vgrad(26, 42, "#ffa6ad", "#c9404d"), stroke=INK, width=1.6)
    a.add(poly(star_points(18, 18, 7, 15, 7, -90)), fill=vgrad(3, 33, "#d7a0e0", "#8a4aa0"), stroke=INK, width=1.6)
    a.add(circle(18, 18, 3.6), fill=INK)
    for x, y in ((26, 28), (30, 27), (28, 32)):
        a.add(circle(x, y, 1.1), fill=CREAM)


def _bleach(a):
    a.add(ellipse(24, 42, 14, 3.5), fill="#8d8173", stroke=INK, width=1.4)
    for p, w0, w1 in (([(24, 42), (23, 30), (20, 18)], 6, 4), ([(23, 32), (32, 24), (34, 16)], 4.4, 3), ([(22, 26), (14, 20), (12, 12)], 4, 2.8)):
        a.add(taper(p, w0, w1), fill=vgrad(10, 42, "#ffffff", "#d9d2c0"), stroke=INK, width=1.4)
    for x, y in ((38, 8), (8, 30)):
        a.add(poly(star_points(x, y, 4, 5, 1.8)), fill="#fff3a0", stroke=INK, width=0.8)
    a.add(rrect(34, 30, 11, 15, 2), fill=vgrad(30, 45, "#fdf7ea", "#d9ccb0"), stroke=INK, width=1.3)


def _cocoon(a):
    a.add(ellipse(24, 26, 20, 16), fill=rad(20, 20, 22, (0, "#f4fffb"), (0.7, "#bfeee4"), (1, "#6fcfc0")), stroke=INK, width=1.6)
    fish_shape(a, 22, 28, 0.75, "#5fe0cc")
    for x, y, sz in ((33, 12, 5), (39, 6, 4)):
        a.line(f"M{x},{y} h{sz} l{-sz},{sz} h{sz}", INK, 1.6)


def _ride(a):
    curved_arrow(a, [(4, 38), (16, 42), (30, 36), (44, 30)], "#6fd6e2", 5, 4.5, 11)
    a.add(ellipse(22, 22, 11, 8.5), fill=vgrad(13, 31, "#b6e38a", "#3f7f2a"), stroke=INK, width=1.6)
    a.add(poly([(22, 16), (27, 19), (27, 25), (22, 28), (17, 25), (17, 19)]), fill="#7cc052", stroke=INK, width=1)
    a.add(circle(35, 20, 3.8), fill="#9fd873", stroke=INK, width=1.3)


def _molt(a):
    a.add(taper([(4, 36), (14, 30), (12, 20), (22, 12), (32, 16), (36, 8)], 7, 5), fill=CREAM, stroke="#3d7fb0", width=1.4)
    a.add(taper([(10, 44), (20, 38), (18, 28), (28, 20), (38, 24), (42, 16)], 7, 5), fill="#8fd3ee", stroke=INK, width=1.8)
    a.add(ellipse(43, 13, 5, 3.6), fill="#2a5578", stroke=INK, width=1.3)
    a.add(circle(8, 10, 7), fill="#8be07a", stroke=INK, width=1.4)
    a.line("M8,6 V14 M4,10 H12", INK, 2.2)


def _clean(a):
    fish_shape(a, 20, 30, 1.2, "#9fc0cf")
    fish_shape(a, 34, 36, 0.5, "#c2b8a4")
    for x, y, r in ((36, 12, 6), (26, 8, 3.5), (44, 22, 3)):
        a.add(poly(star_points(x, y, 4, r, r * 0.35)), fill="#fff3a0", stroke=INK, width=0.9)


def _hitch(a):
    a.add(blob([(4, 30), (14, 22), (32, 22), (44, 28), (32, 34), (14, 34)]), fill=vgrad(22, 34, "#9fc0cf", "#46687a"), stroke=INK, width=1.6)
    a.add(ellipse(24, 38, 10, 3.2), fill="#c2b8a4", stroke=INK, width=1.2)
    a.add(circle(36, 12, 8.5), fill="#8be07a", stroke=INK, width=1.6)
    a.line("M36,7.5 V16.5 M31.5,12 H40.5", INK, 2.6)


def _swap(a):
    for x, c in ((8, "#fdf7ea"), (28, "#fdf7ea")):
        a.add(rrect(x, 14, 13, 19, 3), fill=vgrad(14, 33, c, "#d9ccb0"), stroke=INK, width=1.4)
    curved_arrow(a, [(12, 12), (24, 4), (36, 11)], "#6fd6e2", 3, 3, 7)
    curved_arrow(a, [(36, 36), (24, 44), (12, 37)], "#ff8c6b", 3, 3, 7)


def _ink(a):
    a.add(blob([(4, 30), (8, 18), (18, 12), (28, 16), (32, 26), (28, 38), (16, 42), (6, 38)]), fill=rad(16, 24, 18, (0, "#3c4150"), (1, "#0d1016")), stroke=INK, width=1.6)
    for x, y, r in ((34, 36, 3), (8, 8, 2.2), (30, 8, 2)):
        a.add(circle(x, y, r), fill="#1c2229")
    curved_arrow(a, [(26, 24), (34, 20), (40, 14), (45, 8)], "#e06a7b", 4, 3.6, 9)


def _hatch(a):
    a.add("M10,26 C10,12 18,6 24,6 C30,6 38,12 38,26 L33,22 L28,27 L23,22 L18,27 L14,22 Z", fill=rad(20, 12, 18, (0, "#ffffff"), (1, "#d6ceb8")), stroke=INK, width=1.4)
    a.add("M10,30 L14,26 L18,31 L23,26 L28,31 L33,26 L38,30 C38,40 32,44 24,44 C16,44 10,40 10,30 Z", fill=rad(20, 32, 18, (0, "#ffffff"), (1, "#d6ceb8")), stroke=INK, width=1.4)
    a.add(ellipse(24, 29, 8, 4.5), fill="#c49ae0", stroke=INK, width=1.2)
    a.add(circle(21, 28.5, 1.2), fill=INK)


def _hypnotize(a):
    pts = []
    for i in range(60):
        t = i / 59
        ang = t * 3.2 * 360
        r = 2 + 18 * t
        pts.append(arc_pt(24, 24, r, ang))
    a.add(smooth(pts, closed=False), stroke="#d4577f", width=3)
    a.add(smooth(pts, closed=False), stroke=INK, width=1)


def _pulse(a):
    a.add(smooth(ring_points(24, 22, 12, 11, 180, 360, 7) + [(35, 23), (29, 25), (24, 23), (19, 25), (13, 23)]), fill=vgrad(10, 25, "#e6d0ff", "#8656b4"), stroke=INK, width=1.6)
    for x in (16, 22, 28, 34):
        a.add(taper([(x, 24), (x - 1, 30), (x + 1, 36)], 2, 0.8), fill="#c6a2ee")
    for r in (17, 21):
        a.add(smooth(ring_points(24, 22, r, r, 200, 340, 7), closed=False), stroke="#ffcf6a", width=2)


def _dawn(a):
    a.add("M4,34 A20,20 0 0,1 44,34 Z", fill=rad(24, 34, 20, (0, "#fff6c0"), (1, "#ffb347")), stroke=INK, width=1.8)
    for deg in (190, 220, 250, 290, 320, 350):
        a.line(poly([arc_pt(24, 34, 23, deg), arc_pt(24, 34, 28, deg)], closed=False), "#ffd27a", 2.2)
    a.add(smooth([(2, 36), (10, 33.5), (18, 36.5), (26, 33.5), (34, 36.5), (42, 33.5), (46, 36), (46, 44), (2, 44)]), fill="#3aa0c8", stroke=INK, width=1.4)
    arrowhead(a, (24, 5), (24, 13), 9, "#fff3a0")


def _day(a):
    for deg in range(0, 360, 45):
        a.add(taper([arc_pt(24, 24, 13, deg), arc_pt(24, 24, 21, deg)], 4, 1.6), fill="#ffd27a", stroke=INK, width=1.2)
    a.add(circle(24, 24, 11), fill=rad(21, 21, 13, (0, "#fffbe0"), (1, "#ffc23a")), stroke=INK, width=1.8)


ICONS = {
    "move": _move, "swim": _swim, "hunt": _hunt, "battle": _battle, "bite": _bite, "snap": _snap, "arrive": _arrive,
    "spawn": _spawn, "grow": _grow, "build": _build, "craft": _craft, "card": _card, "extra": _extra, "ambush": _ambush,
    "dominance": _dominance, "endday": _endday, "done": _done, "baitball": _baitball, "gorge": _gorge, "current": _current,
    "drift": _drift, "graze": _graze, "feed": _feed, "lay": _lay, "attach": _attach, "letgo": _letgo, "shell": _shell,
    "price": _price, "lure": _lure, "steal": _steal, "recoil": _recoil, "order": _order, "reach": _reach, "grab": _grab,
    "mantle": _mantle, "paint": _paint, "dig": _dig, "leave": _leave, "sandbar": _sandbar, "island": _island,
    "slither": _slither, "setup": _setup, "nest": _nest, "market": _market, "recruit": _recruit, "dawn": _dawn, "day": _day,
    "feedblood": _feedblood, "frenzy": _frenzy, "rally": _rally, "push": _push, "release": _release, "breed": _breed,
    "devour": _devour, "bleach": _bleach, "cocoon": _cocoon, "ride": _ride, "molt": _molt, "clean": _clean, "hitch": _hitch,
    "swap": _swap, "ink": _ink, "hatch": _hatch, "hypnotize": _hypnotize, "pulse": _pulse,
}


# ---- Map scenery ------------------------------------------------------------------------------------------


def deco_kelp():
    a = Art("deco_kelp", 64, 64, "deco")
    for i, (x, h, sway) in enumerate(((12, 50, 5), (24, 60, -6), (36, 44, 4), (48, 56, -5), (56, 36, 3))):
        pts = [(x, 64), (x + sway, 64 - h * 0.35), (x - sway, 64 - h * 0.7), (x + sway * 0.5, 64 - h)]
        a.add(taper(pts, 4.5, 2), fill=vgrad(4, 64, "#9fd873", "#2f6a22"), stroke="#1a3d12", width=1.2, alpha=0.95)
        for t in (0.3, 0.55, 0.8):
            y = 64 - h * t
            a.add(taper([(x, y), (x + (7 if i % 2 else -7), y - 5)], 3.4, 1), fill="#7cc052", alpha=0.9)
    return a


def deco_sponge():
    a = Art("deco_sponge", 64, 64, "deco")
    ink = "#6a3d08"
    for x, y, w, h, c in ((6, 34, 12, 30, "#f0b95a"), (40, 26, 14, 38, "#e8963a"), (20, 40, 18, 24, "#f7cf6e")):
        a.add(rrect(x, y, w, h, 5), fill=vgrad(y, 64, lighten(c, 0.3), darken(c, 0.25)), stroke=ink, width=1.3)
        a.add(ellipse(x + w / 2, y + 3.5, w / 2 - 2, 2.2), fill="#7a4a0e")
    a.add(circle(56, 56, 7), fill=rad(54, 54, 9, (0, "#ffb3a0"), (1, "#d0603e")), stroke=ink, width=1.2)
    for x, y in ((54, 54), (58, 58), (55, 59)):
        a.add(circle(x, y, 1), fill="#8a3a1e")
    return a


def deco_pearl():
    a = Art("deco_pearl", 64, 64, "deco")
    ink = "#2f2f5a"
    for (x, y, s) in ((18, 50, 1.0), (44, 52, 0.85), (32, 38, 0.7)):
        with a.group(tx=x - 24 * s, ty=y - 24 * s, sx=s):
            a.add(blob([(4, 30), (12, 24), (24, 22), (36, 24), (44, 30), (38, 40), (24, 44), (10, 40)]), fill=vgrad(20, 46, "#dfe5fb", "#7f8fc9"), stroke=ink, width=1.6)
            a.add(blob([(6, 26), (10, 16), (24, 10), (38, 14), (43, 24), (36, 22), (24, 20), (12, 22)]), fill=vgrad(10, 26, "#f2f4ff", "#9aa7dc"), stroke=ink, width=1.6)
            a.add(circle(24, 26, 5), fill=rad(22, 24, 7, (0, "#ffffff"), (1, "#c9c2f0")), stroke=ink, width=1)
    return a


def deco_rock():
    a = Art("deco_rock", 64, 64, "deco")
    a.add(blob([(4, 60), (8, 40), (20, 26), (34, 22), (48, 30), (58, 44), (60, 60)]), fill=vgrad(22, 62, "#9aa3a6", "#4c5558"), stroke="#20282b", width=1.6)
    a.add(blob([(20, 28), (34, 23), (46, 30), (34, 30)]), fill="#f4f6f4", alpha=0.85)
    with a.group(tx=30, ty=8):
        a.add("M0,6 C4,0 10,0 14,5 C18,0 24,0 28,6", stroke="#20282b", width=2.2)
    return a


def deco_wreck():
    a = Art("deco_wreck", 64, 64, "deco")
    ink = "#2a1a0e"
    a.add(poly([(4, 50), (10, 34), (48, 30), (60, 42), (56, 58), (12, 60)]), fill=vgrad(30, 60, "#9a6a3e", "#4a2e16"), stroke=ink, width=1.6)
    for y in (40, 48):
        a.line(f"M8,{y} L58,{y - 4}", ink, 1.2, alpha=0.7)
    a.add(taper([(30, 32), (28, 14), (26, 4)], 3.2, 2.2), fill="#6a4a2a", stroke=ink, width=1)
    a.add(poly([(28, 10), (44, 16), (28, 22)]), fill="#d9cbb0", stroke=ink, width=1, alpha=0.85)
    a.add(circle(44, 44, 3), fill="#1a1008")
    return a


def deco_driftwood():
    a = Art("deco_driftwood", 64, 64, "deco")
    ink = "#3a2412"
    a.add(taper([(4, 50), (22, 44), (44, 42), (60, 36)], 10, 6), fill=vgrad(34, 56, "#c8a47a", "#7a5634"), stroke=ink, width=1.4)
    a.add(taper([(30, 44), (36, 32), (40, 22)], 4.5, 2), fill="#a8845a", stroke=ink, width=1.2)
    for x in (14, 28, 44):
        a.line(smooth([(x, 44), (x + 6, 43), (x + 12, 41)], closed=False), "#6a4a2a", 1, alpha=0.8)
    return a


def deco_arch():
    a = Art("deco_arch", 64, 64, "deco")
    a.add("M2,62 C2,34 14,14 32,14 C50,14 62,34 62,62 H48 C48,42 42,30 32,30 C22,30 16,42 16,62 Z",
          fill=vgrad(14, 62, "#a39a8a", "#554d42"), stroke="#231e18", width=1.6)
    for x, y in ((12, 40), (52, 44), (30, 18)):
        a.add(circle(x, y, 2.2), fill="#ff939a", stroke="#6a1b27", width=0.7)
    return a


def deco_hole():
    a = Art("deco_hole", 64, 64, "deco")
    a.add(ellipse(32, 36, 28, 20), fill=rad(32, 38, 28, (0, "#020a14"), (0.7, "#0a2a4a"), (1, "#1f5a7a", 0.6)))
    a.add(ellipse(32, 36, 28, 20), stroke="#7fd3e6", width=1.2, alpha=0.5)
    return a


DECOS = [deco_kelp, deco_sponge, deco_pearl, deco_rock, deco_wreck, deco_driftwood, deco_arch, deco_hole]
SUITS = [suit_kelp, suit_sponge, suit_pearl, suit_moon]
PIECES = [piece_coral, piece_market, piece_nest, piece_egg, piece_blood, piece_rubble, lure_treasure, lure_shelter, lure_glory,
          lambda: pigment("pigment_kelp", SUIT["kelp"]), lambda: pigment("pigment_sponge", SUIT["sponge"]),
          lambda: pigment("pigment_pearl", SUIT["pearl"]), piece_shell, piece_island, piece_sandbar, lure_dark, piece_cyst, piece_cocoon]


def all_art():
    return [fn() for fn in SUITS] + [fn() for fn in PIECES] + [icon(k, v) for k, v in ICONS.items()] + [fn() for fn in DECOS]
