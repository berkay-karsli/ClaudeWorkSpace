"""Writes a contact sheet of all art to an HTML file: python3 preview.py OUT.html [category]."""
import sys

from artlib import to_svg
import catalog

out = sys.argv[1]
only = sys.argv[2] if len(sys.argv) > 2 else None
cells = []
for art in catalog.all_art():
    if only and art.category != only:
        continue
    scale = 2 if art.w >= 100 else 3
    svg = to_svg(art).replace(f'width="{int(art.w)}" height="{int(art.h)}"', f'width="{int(art.w * scale)}" height="{int(art.h * scale)}"')
    cells.append(f'<figure><div class="bg">{svg}</div><figcaption>{art.name}</figcaption></figure>')
html = """<!doctype html><meta charset="utf-8"><style>
body{margin:0;padding:16px;background:#0d2a3a;font:12px system-ui;color:#cfe;display:flex;flex-wrap:wrap;gap:12px}
figure{margin:0;text-align:center}
.bg{background:radial-gradient(circle at 50% 40%,#2c6e86,#123a4d);border-radius:14px;padding:6px}
</style>""" + "".join(cells)
open(out, "w").write(html)
print(len(cells), "pictures")
