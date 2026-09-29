"""
The linocut look: every picture is printed with dark ink, cream paper and a few flat print
colors. Portraits are drawn with these tools directly; the smaller pictures (tokens, icons,
scenery) are drawn once and then pressed onto the print palette by [press].
"""
import colorsys
import math

from artlib import Art, Group, Path, Solid, Linear, Radial, circle, ellipse, smooth, f, rgb, hexc

INK = "#1c2229"
CREAM = "#f3e7cc"
PAPER = "#efe2c4"

# The print inks, one per hue family.
INKS = {
    "red": "#c8472d",
    "orange": "#d9803a",
    "yellow": "#e2b43e",
    "green": "#5b8a3a",
    "teal": "#2a9488",
    "blue": "#3d7fb0",
    "violet": "#7d5bb0",
    "pink": "#d4577f",
    "sand": "#b89b6a",
}


def outline(width=3.5):
    return dict(stroke=INK, width=width)


def carve(a, clip_d, lines, width=1.6, color=CREAM, alpha=1.0):
    """Lines cut into a shape: strokes that stay inside it."""
    with a.group(clip=clip_d):
        for d in lines:
            a.line(d, color, width, alpha=alpha)


def hatch(a, clip_d, angle=35, spacing=4.0, width=1.3, color=INK, box=(0, 0, 120, 120)):
    """Parallel straight lines across [box], kept inside the shape: printed shading."""
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    r = math.hypot(x1 - x0, y1 - y0) / 2 + 2
    a_rad = math.radians(angle)
    ux, uy = math.cos(a_rad), math.sin(a_rad)
    nx, ny = -uy, ux
    lines = []
    k = -r
    while k <= r:
        px, py = cx + nx * k, cy + ny * k
        lines.append(f"M{f(px - ux * r)},{f(py - uy * r)} L{f(px + ux * r)},{f(py + uy * r)}")
        k += spacing
    carve(a, clip_d, lines, width, color)


def dots(a, pts, r=1.6, color=CREAM):
    for x, y in pts:
        a.add(circle(x, y, r), fill=color)


def lino_eye(a, x, y, r):
    """A printed eye: cream disc ringed in ink, a round ink pupil and a cut glint."""
    a.add(circle(x, y, r), fill=CREAM, stroke=INK, width=max(1.4, r * 0.38))
    a.add(circle(x + r * 0.12, y + r * 0.05, r * 0.52), fill=INK)
    a.add(circle(x + r * 0.3, y - r * 0.22, r * 0.17), fill=CREAM)


# ---- Pressing any picture onto the print palette ---------------------------------------------------


def _hls(c):
    r, g, b = (v / 255 for v in rgb(c))
    return colorsys.rgb_to_hls(r, g, b)


def to_print(c, accent=None):
    """The print color for [c]: paper for light colors, ink for dark ones, else the nearest print ink."""
    h, l, s = _hls(c)
    if l >= 0.78 or (l >= 0.68 and s < 0.35):
        return CREAM
    if l <= 0.24:
        return INK
    if s < 0.18:
        return INKS["sand"] if l > 0.45 else INK
    if accent:
        return accent
    best, dist = None, 9
    for name, ink in INKS.items():
        if name == "sand":
            continue
        ih, il, isat = _hls(ink)
        d = min(abs(h - ih), 1 - abs(h - ih))
        if d < dist:
            best, dist = ink, d
    return best


def _mid(p):
    """The one color a gradient prints as: the color at its middle."""
    stops = p.stops
    if len(stops) == 1:
        return stops[0][1], stops[0][2]
    for (o0, c0, a0), (o1, c1, a1) in zip(stops, stops[1:]):
        if o0 <= 0.5 <= o1:
            t = 0 if o1 == o0 else (0.5 - o0) / (o1 - o0)
            ra, rb = rgb(c0), rgb(c1)
            return hexc(*(x + (y - x) * t for x, y in zip(ra, rb))), a0 + (a1 - a0) * t
    return stops[-1][1], stops[-1][2]


def _press_paint(p, accent):
    if p is None:
        return None
    if isinstance(p, Solid):
        return Solid(to_print(p.color, None if p.color.lower() in ("#ffffff", "#fff") else accent), p.alpha)
    if isinstance(p, (Linear, Radial)):
        c, alpha = _mid(p)
        # A glow (a gradient that fades out) prints as a pale disc.
        if min(s[2] for s in p.stops) < 0.2:
            return Solid(CREAM, 0.35)
        return Solid(to_print(c, accent), alpha)
    return p


def _press_nodes(nodes, accent, ink_scale):
    for n in nodes:
        if isinstance(n, Group):
            _press_nodes(n.children, accent, ink_scale)
            continue
        n.fill = _press_paint(n.fill, accent)
        if n.stroke is not None:
            if n.fill is None and n.width >= 2.5:
                # A thick stroke is a shape in its own right (a branch, an arrow's shaft).
                n.stroke = _press_paint(n.stroke, accent)
            else:
                n.stroke = Solid(INK, 1.0)
                n.width = n.width * ink_scale


def press(art, accent=None, ink_scale=1.35):
    """Presses [art] onto the print palette in place and returns it."""
    _press_nodes(art.children, accent, ink_scale)
    return art
