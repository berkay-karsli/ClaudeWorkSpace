"""Every picture in the game, in the order the generated Kotlin lists them."""
import creatures
import pieces
from lino import press

# Pictures whose main color must match a faction's print color rather than the nearest print ink.
ACCENTS = {"piece_coral": "#e0606a", "icon_grow": "#e0606a"}


def all_art():
    # Portraits are drawn as prints; everything else is drawn once and pressed onto the print palette.
    return [fn() for fn in creatures.ALL] + [press(a, ACCENTS.get(a.name)) for a in pieces.all_art()]
