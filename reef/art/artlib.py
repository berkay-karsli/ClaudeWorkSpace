"""
A tiny vector-art toolkit. Art is built as a scene of paths and groups in Python, then written out
twice from the same scene: as SVG (for previews and the design guide) and as Kotlin ImageVector
code (for the app). Nothing is parsed back, so the two can never disagree.

Coordinates are in the art's own viewport. Groups use Compose's transform model:
translate(tx + px, ty + py) · rotate(rot) · scale(sx, sy) · translate(-px, -py).
"""
import math

# ---- Colors -------------------------------------------------------------------------------------


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def hexc(r, g, b):
    return "#%02x%02x%02x" % tuple(max(0, min(255, round(v))) for v in (r, g, b))


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return hexc(*(x + (y - x) * t for x, y in zip(ra, rb)))


def lighten(c, t):
    return mix(c, "#ffffff", t)


def darken(c, t):
    return mix(c, "#000000", t)


# ---- Paints -------------------------------------------------------------------------------------


class Solid:
    def __init__(self, color, alpha=1.0):
        self.color, self.alpha = color, alpha


class Linear:
    def __init__(self, x1, y1, x2, y2, stops):
        self.x1, self.y1, self.x2, self.y2 = x1, y1, x2, y2
        self.stops = [s if len(s) == 3 else (s[0], s[1], 1.0) for s in stops]


class Radial:
    def __init__(self, cx, cy, r, stops):
        self.cx, self.cy, self.r = cx, cy, r
        self.stops = [s if len(s) == 3 else (s[0], s[1], 1.0) for s in stops]


def paint(p):
    if p is None or isinstance(p, (Solid, Linear, Radial)):
        return p
    return Solid(p)


def lin(x1, y1, x2, y2, *stops):
    return Linear(x1, y1, x2, y2, list(stops))


def rad(cx, cy, r, *stops):
    return Radial(cx, cy, r, list(stops))


def vgrad(y1, y2, top, bottom):
    """A vertical gradient, the most common light-from-above shading."""
    return Linear(0, y1, 0, y2, [(0, top), (1, bottom)])


# ---- Scene --------------------------------------------------------------------------------------


class Path:
    def __init__(self, d, fill=None, stroke=None, width=0.0, alpha=1.0, cap="round", join="round", evenodd=False):
        self.d, self.fill, self.stroke = d, paint(fill), paint(stroke)
        self.width, self.alpha, self.cap, self.join, self.evenodd = width, alpha, cap, join, evenodd


class Group:
    def __init__(self, tx=0.0, ty=0.0, rot=0.0, px=0.0, py=0.0, sx=1.0, sy=None):
        self.tx, self.ty, self.rot, self.px, self.py = tx, ty, rot, px, py
        self.sx = sx
        self.sy = sx if sy is None else sy
        self.children = []


class Art:
    """One picture. Draw into it with [add] and [group]; later calls paint on top."""

    def __init__(self, name, w, h, category):
        self.name, self.w, self.h, self.category = name, w, h, category
        self.children = []
        self._stack = [self.children]

    def add(self, d, fill=None, stroke=None, width=0.0, alpha=1.0, **kw):
        if not d:
            return
        self._stack[-1].append(Path(d, fill, stroke, width, alpha, **kw))

    def line(self, d, color, width, alpha=1.0, cap="round"):
        self.add(d, stroke=color, width=width, alpha=alpha, cap=cap)

    def group(self, **kw):
        art = self

        class _Ctx:
            def __enter__(self_inner):
                g = Group(**kw)
                art._stack[-1].append(g)
                art._stack.append(g.children)
                return g

            def __exit__(self_inner, *a):
                art._stack.pop()

        return _Ctx()


# ---- Geometry -----------------------------------------------------------------------------------


def f(x):
    s = ("%.1f" % x).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def pt(p):
    return f"{f(p[0])},{f(p[1])}"


def poly(points, closed=True):
    d = "M" + pt(points[0]) + "".join(" L" + pt(p) for p in points[1:])
    return d + (" Z" if closed else "")


def _cr_controls(p0, p1, p2, p3, tension=1.0):
    c1 = (p1[0] + (p2[0] - p0[0]) / 6 * tension, p1[1] + (p2[1] - p0[1]) / 6 * tension)
    c2 = (p2[0] - (p3[0] - p1[0]) / 6 * tension, p2[1] - (p3[1] - p1[1]) / 6 * tension)
    return c1, c2


def smooth(points, closed=True, tension=1.0):
    """A smooth curve through every point (Catmull-Rom as cubic Béziers)."""
    n = len(points)
    if n < 3:
        return poly(points, closed)
    d = "M" + pt(points[0])
    segs = n if closed else n - 1
    for i in range(segs):
        p0 = points[(i - 1) % n] if closed else points[max(i - 1, 0)]
        p1 = points[i]
        p2 = points[(i + 1) % n]
        p3 = points[(i + 2) % n] if closed else points[min(i + 2, n - 1)]
        c1, c2 = _cr_controls(p0, p1, p2, p3, tension)
        d += f" C{pt(c1)} {pt(c2)} {pt(p2)}"
    return d + (" Z" if closed else "")


blob = smooth


def spline_points(points, k=10):
    """Dense samples along the open smooth curve through [points]."""
    n = len(points)
    out = [points[0]]
    for i in range(n - 1):
        p0, p1, p2 = points[max(i - 1, 0)], points[i], points[i + 1]
        p3 = points[min(i + 2, n - 1)]
        c1, c2 = _cr_controls(p0, p1, p2, p3)
        for j in range(1, k + 1):
            t = j / k
            mt = 1 - t
            out.append((
                mt ** 3 * p1[0] + 3 * mt * mt * t * c1[0] + 3 * mt * t * t * c2[0] + t ** 3 * p2[0],
                mt ** 3 * p1[1] + 3 * mt * mt * t * c1[1] + 3 * mt * t * t * c2[1] + t ** 3 * p2[1],
            ))
    return out


def _normals(pts):
    out = []
    for i in range(len(pts)):
        a = pts[max(i - 1, 0)]
        b = pts[min(i + 1, len(pts) - 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(dx, dy) or 1
        out.append((-dy / ln, dx / ln))
    return out


def taper(points, w0, w1, k=8, ease=1.0, start_cap=True):
    """
    A filled stroke along the smooth curve through [points], [w0] wide at the start and [w1] at
    the end, with round ends. Fins, tentacles, branches and spines are all tapers.
    """
    pts = spline_points(points, k)
    nrm = _normals(pts)
    m = len(pts) - 1
    widths = [w0 + (w1 - w0) * ((i / m) ** ease) for i in range(m + 1)]
    left = [(p[0] + n[0] * w / 2, p[1] + n[1] * w / 2) for p, n, w in zip(pts, nrm, widths)]
    right = [(p[0] - n[0] * w / 2, p[1] - n[1] * w / 2) for p, n, w in zip(pts, nrm, widths)]
    # Round end: a few points around the tip.
    end, en = pts[-1], nrm[-1]
    ed = (en[1], -en[0])  # direction of travel
    tip = []
    for a in (60, 120):
        r = math.radians(a)
        c, s = math.cos(r), math.sin(r)
        tip.append((end[0] + (en[0] * c + ed[0] * s) * w1 / 2, end[1] + (en[1] * c + ed[1] * s) * w1 / 2))
    start, sn = pts[0], nrm[0]
    sd = (-sn[1], sn[0])  # backwards
    cap = []
    if start_cap:
        for a in (60, 120):
            r = math.radians(a)
            c, s = math.cos(r), math.sin(r)
            cap.append((start[0] - (sn[0] * c - sd[0] * s) * w0 / 2 + 0, start[1] - (sn[1] * c - sd[1] * s) * w0 / 2))
    outline = left + tip + right[::-1] + cap
    # Thin the points so the path stays small, then smooth.
    return smooth(_thin(outline, 0.9), closed=True)


def _thin(pts, min_dist):
    out = [pts[0]]
    for p in pts[1:]:
        if math.hypot(p[0] - out[-1][0], p[1] - out[-1][1]) >= min_dist:
            out.append(p)
    return out


def circle(cx, cy, r):
    return (f"M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 1,0 {f(cx + r)},{f(cy)} "
            f"A{f(r)},{f(r)} 0 1,0 {f(cx - r)},{f(cy)} Z")


def ellipse(cx, cy, rx, ry):
    return (f"M{f(cx - rx)},{f(cy)} A{f(rx)},{f(ry)} 0 1,0 {f(cx + rx)},{f(cy)} "
            f"A{f(rx)},{f(ry)} 0 1,0 {f(cx - rx)},{f(cy)} Z")


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (f"M{f(x + r)},{f(y)} H{f(x + w - r)} A{f(r)},{f(r)} 0 0,1 {f(x + w)},{f(y + r)} "
            f"V{f(y + h - r)} A{f(r)},{f(r)} 0 0,1 {f(x + w - r)},{f(y + h)} H{f(x + r)} "
            f"A{f(r)},{f(r)} 0 0,1 {f(x)},{f(y + h - r)} V{f(y + r)} A{f(r)},{f(r)} 0 0,1 {f(x + r)},{f(y)} Z")


def arc_pt(cx, cy, r, deg, ry=None):
    a = math.radians(deg)
    return (cx + r * math.cos(a), cy + (ry if ry is not None else r) * math.sin(a))


def ring_points(cx, cy, rx, ry, a0, a1, n):
    return [arc_pt(cx, cy, rx, a0 + (a1 - a0) * i / (n - 1), ry) for i in range(n)]


def star_points(cx, cy, n, r_out, r_in, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        pts.append(arc_pt(cx, cy, r, rot + 180 * i / n))
    return pts


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def along(points, t0, t1, k=10):
    """The part of the smooth curve through [points] between fractions t0 and t1 of its samples."""
    pts = spline_points(points, k)
    m = len(pts) - 1
    return pts[round(t0 * m): round(t1 * m) + 1]


# ---- Output: SVG --------------------------------------------------------------------------------


def _svg_paint(p, defs, prefix):
    if p is None:
        return "none", 1.0
    if isinstance(p, Solid):
        return p.color, p.alpha
    gid = f"{prefix}g{len(defs)}"
    stops = "".join(f'<stop offset="{f(o)}" stop-color="{c}"' + (f' stop-opacity="{f(a)}"' if a != 1 else "") + "/>" for o, c, a in p.stops)
    if isinstance(p, Linear):
        defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(p.x1)}" y1="{f(p.y1)}" x2="{f(p.x2)}" y2="{f(p.y2)}">{stops}</linearGradient>')
    else:
        defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(p.cx)}" cy="{f(p.cy)}" r="{f(p.r)}">{stops}</radialGradient>')
    return f"url(#{gid})", 1.0


def _svg_nodes(nodes, defs, prefix):
    out = []
    for n in nodes:
        if isinstance(n, Group):
            t = f"translate({f(n.tx + n.px)} {f(n.ty + n.py)}) rotate({f(n.rot)}) scale({f(n.sx)} {f(n.sy)}) translate({f(-n.px)} {f(-n.py)})"
            out.append(f'<g transform="{t}">' + _svg_nodes(n.children, defs, prefix) + "</g>")
        else:
            fill, fa = _svg_paint(n.fill, defs, prefix)
            stroke, sa = _svg_paint(n.stroke, defs, prefix)
            attrs = [f'd="{n.d}"', f'fill="{fill}"']
            if fa * n.alpha != 1 and n.fill is not None:
                attrs.append(f'fill-opacity="{f(fa * n.alpha)}"')
            if n.stroke is not None:
                attrs += [f'stroke="{stroke}"', f'stroke-width="{f(n.width)}"', f'stroke-linecap="{n.cap}"', f'stroke-linejoin="{n.join}"']
                if sa * n.alpha != 1:
                    attrs.append(f'stroke-opacity="{f(sa * n.alpha)}"')
            if n.evenodd:
                attrs.append('fill-rule="evenodd"')
            out.append("<path " + " ".join(attrs) + "/>")
    return "".join(out)


def to_svg(art, prefix=None):
    prefix = prefix or art.name.replace("_", "")
    defs = []
    body = _svg_nodes(art.children, defs, prefix)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {f(art.w)} {f(art.h)}" width="{f(art.w)}" height="{f(art.h)}">'
            + (f"<defs>{''.join(defs)}</defs>" if defs else "") + body + "</svg>")


# ---- Output: Kotlin -----------------------------------------------------------------------------


def _kt_color(c, a=1.0):
    r, g, b = rgb(c)
    return "0x%02X%02X%02X%02X" % (round(a * 255), r, g, b)


def _kt_float(x):
    return f(x) + "f"


def _kt_paint(p):
    if isinstance(p, Solid):
        return f"c({_kt_color(p.color, p.alpha)})"
    stops = ", ".join(f"{_kt_float(o)} to {_kt_color(c, a)}" for o, c, a in p.stops)
    if isinstance(p, Linear):
        return f"lin({_kt_float(p.x1)}, {_kt_float(p.y1)}, {_kt_float(p.x2)}, {_kt_float(p.y2)}, {stops})"
    return f"rad({_kt_float(p.cx)}, {_kt_float(p.cy)}, {_kt_float(p.r)}, {stops})"


def _kt_nodes(nodes, indent):
    pad = " " * indent
    out = []
    for n in nodes:
        if isinstance(n, Group):
            args = []
            for key, default in (("tx", 0), ("ty", 0), ("rot", 0), ("px", 0), ("py", 0), ("sx", 1), ("sy", 1)):
                v = getattr(n, key)
                if abs(v - default) > 1e-9:
                    args.append(f"{key} = {_kt_float(v)}")
            out.append(f"{pad}g({', '.join(args)}) {{")
            out += _kt_nodes(n.children, indent + 4)
            out.append(f"{pad}}}")
        else:
            args = [f'"{n.d}"']
            if n.fill is not None:
                args.append(f"f = {_kt_paint(n.fill)}")
            if n.stroke is not None:
                args.append(f"s = {_kt_paint(n.stroke)}")
                args.append(f"w = {_kt_float(n.width)}")
                if n.cap != "round":
                    args.append(f"cap = StrokeCap.{n.cap.capitalize()}")
            if n.alpha != 1:
                args.append(f"a = {_kt_float(n.alpha)}")
            if n.evenodd:
                args.append("eo = true")
            out.append(f"{pad}p({', '.join(args)})")
    return out


def to_kotlin(art, val_name):
    lines = [f"    val {val_name}: ImageVector by lazy {{",
             f'        art("{art.name}", {_kt_float(art.w)}, {_kt_float(art.h)}) {{']
    lines += _kt_nodes(art.children, 12)
    lines += ["        }", "    }"]
    return "\n".join(lines)
