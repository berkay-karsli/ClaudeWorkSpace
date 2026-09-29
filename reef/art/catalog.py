"""Every picture in the game, in the order the generated Kotlin lists them."""
import creatures


def all_art():
    out = [fn() for fn in creatures.ALL]
    try:
        import pieces
        out += pieces.all_art()
    except ImportError:
        pass
    return out
