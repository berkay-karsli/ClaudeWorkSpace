"""Builds field-guide.html, the Reef design document.

All faction rules live in FACTIONS below. The page is generated from this data, and
check() refuses to build when the design breaks one of its own consistency rules.

    python3 build_field_guide.py [output.html]
"""
import math
import re
import sys
from pathlib import Path

VERBS = ["Move", "Battle", "Build", "Recruit", "Craft"]
RULE_THRESHOLDS = {2: 12, 3: 15, 4: 18}  # minimum total reach per faction count

FACTIONS = [
    dict(
        key="sharks", name="Sharks", role="Predator", glyph="shark", color="shark",
        latin="Carcharhinus amblyrhynchos", common="grey reef sharks",
        complexity=1, reach=9,
        tagline="Stop swimming and you drown.",
        idea="Sharks own nothing and build nothing. They roam, they fight, and they score from the blood that every fight leaves behind.",
        core="Must keep moving; feeds on the aftermath of every fight",
        scores_by="eating Blood",
        verbs=dict(Move=True, Battle=True, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Keep swimming", "At Dusk, every shark that didn't move this turn drowns."),
            ("Big", "Each shark counts as two warriors whenever warriors are counted, including for rule and for the hits you can deal."),
            ("Blood in the water", "While sharks are in the game, any attack that removes a warrior leaves a Blood token in that reef, one per reef. Blood belongs to no one. Your sharks can move to a Blood reef up to two channels away, ignoring rule."),
        ],
        pieces="10 sharks. 8 Blood tokens.",
        setup="3 sharks at any gate.",
        turn=[("Dawn", "1 shark arrives at any gate."), ("Day", "3 actions: hunt. A hunt moves sharks from one reef, then they may battle where they arrive."), ("Dusk", "Feed, then any shark that stayed still drowns.")],
        scores=["At Dusk, remove every Blood token in a reef with your sharks: 1 VP each."],
        note="Many sharks breathe by swimming, pushing water over their gills. For them, stopping means suffocating.",
    ),
    dict(
        key="sardines", name="Sardines", role="Racer", glyph="shoal", color="sardine",
        latin="Sardinops sagax", common="sardines on their run",
        complexity=1, reach=2,
        tagline="Ten thousand fish, one direction.",
        idea="A race across the map. Schools come in through one gate and score by leaving through another. Sardines never start a fight and never stay long.",
        core="Race schools across the map and out the far side",
        scores_by="leaving through a gate",
        verbs=dict(Move=True, Battle=False, Build=False, Recruit=True, Craft=False),
        rules=[
            ("The run", "At Dawn, 3 sardines arrive at any gate, plus 1 for each card of that gate's suit you discard. The game remembers the gate every fish came in by."),
            ("One school", "A move takes all your sardines in a reef together, up to two channels, ignoring rule."),
            ("Bait ball", "When battled, before any hits, you may give up the fight: lose half the school, rounded down, and move the rest to a neighboring reef. The lost fish count as removed by the attack."),
        ],
        pieces="30 sardines.",
        setup="5 sardines at any gate.",
        turn=[("Dawn", "The run arrives."), ("Day", "3 actions: move."), ("Dusk", "Schools on a gate may leave the map.")],
        scores=["At Dusk, sardines on a gate they didn't come in by may leave: 1–3 fish score 1 VP, 4–6 score 2, 7–9 score 3, 10 or more score 5. They go back to your supply."],
        note="Every winter billions of sardines run up the east coast of South Africa, and the predators follow them.",
    ),
    dict(
        key="lionfish", name="Lionfish", role="Invader", glyph="lion", color="lion",
        latin="Pterois volitans", common="red lionfish",
        complexity=1, reach=8,
        tagline="Nothing here eats us.",
        idea="An invasive species with no natural enemies on this reef. Two fish slip in at the start. They breed every turn and eat whatever they outnumber.",
        core="Outbreed everyone and eat what you outnumber",
        scores_by="gorging",
        verbs=dict(Move=True, Battle=False, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Breed", "At Dusk, add 1 lionfish to each reef where you have 2 or more."),
            ("Gorge", "Attack a faction whose warriors you outnumber in a reef: it takes 1 hit. No dice, and no hits back."),
            ("Venom", "A faction that battles your lionfish takes 1 hit before the dice are rolled."),
        ],
        pieces="24 lionfish.",
        setup="2 lionfish at any gate.",
        turn=[("Dawn", "Nothing."), ("Day", "3 actions: move or gorge."), ("Dusk", "Breed.")],
        scores=["1 VP for each warrior your gorging removes."],
        note="Lionfish reached the Atlantic in the 1980s and spread along the whole American coast. Local fish don't recognize them as predators.",
    ),
    dict(
        key="starfish", name="Starfish", role="Outbreak", glyph="star", color="star",
        latin="Acanthaster planci", common="crown-of-thorns starfish",
        complexity=1, reach=5,
        tagline="Cut one, two grow back.",
        idea="A plague that eats reefs down to rubble. Starfish never start a fight. They crawl in, strip the reef bare, and hitting them only makes more.",
        core="Strip reefs to rubble; hitting them spreads them",
        scores_by="reefs with Rubble",
        verbs=dict(Move=True, Battle=False, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Strip", "At Dusk, in each reef with 3 or more of your starfish, an empty slot and none of your Rubble, put Rubble in that slot. Rubble is a token that blocks the slot for everyone."),
            ("Regrow", "After any attack that removes your starfish, place 2 starfish from your supply in a reef next to that one."),
            ("Spawn", "At Dawn, discard up to 2 cards. Each adds 2 starfish to a reef of its suit where you have starfish."),
        ],
        pieces="20 starfish. 8 Rubble tokens.",
        setup="3 starfish in each of two neighboring reefs that aren't gates.",
        turn=[("Dawn", "Spawn."), ("Day", "2 actions: move."), ("Dusk", "Strip, then score.")],
        scores=["At Dusk, 1 VP for each reef that has your Rubble."],
        note="A crown-of-thorns outbreak can kill most of the coral on a reef, and a starfish can regrow lost arms.",
    ),
    dict(
        key="coral", name="Coral", role="Builder", glyph="coral", color="coral",
        latin="Acropora cervicornis", common="staghorn coral",
        complexity=2, reach=4,
        tagline="We never move. We grow.",
        idea="A living reef. It can't take a step or start a fight. It spreads by spawning into neighboring reefs and scores by growing.",
        core="Grow in place; spread only by spawning",
        scores_by="growing coral",
        verbs=dict(Move=False, Battle=False, Build=True, Recruit=True, Craft=True),
        rules=[
            ("Rooted", "Polyps never move, not even along currents."),
            ("Spawn", "Once per turn, discard a card: each reef of its suit with your coral puts 1 polyp into every neighboring reef. With a Moon card, every reef with your coral spawns. If you have no coral on the map, the spawn instead puts 2 polyps into one reef of the card's suit."),
            ("Living reef", "When you are battled, each of your coral in that reef counts as a warrior for the hits you can deal."),
        ],
        pieces="20 polyps. 15 coral (buildings).",
        setup="2 coral and 3 polyps in one reef that isn't a gate, and 1 polyp in each neighboring reef.",
        turn=[("Dawn", "Nothing."), ("Day", "3 actions: grow (discard a card of the reef's suit to build a coral in an empty slot of a reef you rule) or spawn."), ("Dusk", "Draw 1 extra card per 5 coral on the map.")],
        scores=["Each coral scores when grown: 1, 1, 1, 2, 2, 2, 3 and so on.", "Gear crafted by your coral."],
        note="Many corals spawn on the same one or two nights a year, timed by the moon. That is why a Moon card makes every reef spawn.",
    ),
    dict(
        key="jellyfish", name="Jellyfish", role="Drifter", glyph="jelly", color="jelly",
        latin="Aurelia aurita", common="moon jellyfish",
        complexity=2, reach=5,
        tagline="We can't steer. We bend the current.",
        idea="You never move a jellyfish. They drift every turn, and all you control is the current that carries them. Whatever swims into them gets stung.",
        core="Drift with the currents you set; sting whatever swims in",
        scores_by="stings and big swarms",
        verbs=dict(Move=False, Battle=False, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Drift", "At Dusk, your jellyfish in each reef follow one current leading out of it, one channel. No current, no drift. Drifting ignores rule."),
            ("Your currents", "You own 4 current arrows. Each turns an empty channel into a one-way current, and currents work for every faction."),
            ("Sting", "When another faction moves warriors into a reef with 2 or more of your jellyfish, by any kind of movement, it takes 1 hit, which removes one of the arrivals."),
        ],
        pieces="24 jellyfish. 4 current arrows (channel markers).",
        setup="3 jellyfish in each of two reefs on the Gyre.",
        turn=[("Dawn", "Nothing."), ("Day", "Place or turn up to 2 arrows. Then bloom: discard up to 2 cards, and each adds 1 jellyfish to every reef of its suit where you have jellyfish (Moon: every such reef)."), ("Dusk", "Drift, then score.")],
        scores=["1 VP each time a sting removes a warrior.", "At Dusk, after drifting, 1 VP per reef with 4 or more of your jellyfish."],
        note="Moon jellyfish blooms can hold millions of animals, enough to clog fishing nets and the cooling intakes of power stations.",
    ),
    dict(
        key="parrotfish", name="Parrotfish", role="Terraformer", glyph="parrot", color="parrot",
        latin="Bolbometopon muricatum", common="bumphead parrotfish",
        complexity=2, reach=7,
        tagline="Every beach was a reef once.",
        idea="Parrotfish chew the reef into sand, then use the sand to reshape the map. Sandbars cut channels, and islands rise out of the sea.",
        core="Graze the reef into sand; reshape the map with it",
        scores_by="sandbars and islands",
        verbs=dict(Move=True, Battle=True, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Graze", "In a reef you rule, remove one enemy building or token and gain 2 Sand. If there is nothing to eat, chew the bare reef for 1 Sand, once per reef each turn."),
            ("Sandbar", "Spend 2 Sand to put a sandbar on a channel next to a reef you rule. For everyone but you, the two reefs stop being neighbors. A faction with warriors at both ends can spend a move to dig it out."),
            ("Island", "Spend 5 Sand to raise an island in a reef you rule that touches 2 of your sandbars. You rule it for the rest of the game, your parrotfish there can't be attacked, and the island can't be removed."),
        ],
        pieces="16 parrotfish. 8 sandbars (channel markers). 3 islands.",
        setup="4 parrotfish at any gate.",
        turn=[("Dawn", "2 parrotfish arrive in a reef you rule, or at a gate if you rule none."), ("Day", "3 actions: move, battle, graze, place a sandbar or raise an island."), ("Dusk", "Score islands.")],
        scores=["1 VP per sandbar placed, 2 VP per island raised.", "At Dusk, 1 VP per island."],
        note="Much of the white sand on tropical beaches has passed through a parrotfish. Bumpheads settle their fights by ramming heads.",
    ),
    dict(
        key="turtles", name="Sea Turtles", role="Wanderer", glyph="turtle", color="turtle",
        latin="Chelonia mydas", common="green sea turtles",
        complexity=2, reach=2,
        tagline="Go far. Come home.",
        idea="Four old turtles swim far out to feed, then come home to lay eggs on the shore. The eggs stay behind, and everyone can see the nest.",
        core="Journey out to feed, come home to nest",
        scores_by="hatching eggs",
        verbs=dict(Move=True, Battle=False, Build=True, Recruit=False, Craft=True),
        rules=[
            ("Shell", "Ignore the first hit of every attack on your turtles."),
            ("Wander", "Turtles move one at a time, one channel per move, ignoring rule."),
            ("Feed and lay", "A turtle away from the shore can feed: it takes a Food of the reef's suit, at most one of each suit. A turtle at your nest lays 1 egg per Food it carries, using the Food up."),
        ],
        pieces="4 turtles. 3 nests (buildings). 10 eggs (tokens).",
        setup="1 nest and all 4 turtles in one shore reef.",
        turn=[("Dawn", "Every egg hatches."), ("Day", "3 actions: move, feed, lay, or build a nest in an empty slot of a shore reef where you have a turtle."), ("Dusk", "Nothing.")],
        scores=["1 VP per egg hatched. Nests score when built: 1, 2, 3.", "Gear crafted at your nests."],
        note="Female sea turtles swim hundreds of kilometers to lay their eggs on the beach where they hatched.",
    ),
    dict(
        key="snake", name="Sea Snake", role="Serpent", glyph="snake", color="snake",
        latin="Laticauda colubrina", common="banded sea krait",
        complexity=2, reach=6,
        tagline="The more it eats, the longer it gets.",
        idea="One snake with one long body stretched across the map. It grows only by eating, and a single hit in the middle can cut it in two.",
        core="One long body; grow by eating, don't get cut",
        scores_by="stretching across the map",
        verbs=dict(Move=True, Battle=True, Build=False, Recruit=True, Craft=False),
        rules=[
            ("One body", "Your Head and segments form a line, each piece in the same reef as the piece ahead of it or next to it. To slither, move the Head one channel, ignoring rule; each segment moves to where the piece ahead of it was."),
            ("Grow", "For each enemy warrior your bite removes, add a segment at your tail."),
            ("Cut", "A hit on your snake always removes the piece nearest the tail among those it can hit. If that splits the body, the part behind the cut is lost. If the Head goes, the next segment becomes the Head; if nothing is left, a new Head arrives at any gate at your next Dawn."),
        ],
        pieces="1 Head and 14 segments, all warriors.",
        setup="The Head and 3 segments in a line of neighboring reefs.",
        turn=[("Dawn", "Nothing."), ("Day", "2 actions, or 3 once you have 8 segments: slither, or bite (battle in the Head's reef)."), ("Dusk", "Score.")],
        scores=["At Dusk, 1 VP for every 2 reefs your snake is in."],
        note="Banded sea kraits hunt eels in reef crevices, then come ashore to digest and lay eggs.",
    ),
    dict(
        key="remoras", name="Remoras", role="Hitchhiker", glyph="remora", color="remora",
        latin="Echeneis naucrates", common="live sharksucker",
        complexity=2, reach=1,
        tagline="Why swim when you can ride?",
        idea="Remoras don't fight, build or hold ground. They stick to other factions' warriors, go where they go, and eat the scraps of every kill.",
        core="Ride other factions and share their kills",
        scores_by="riding and scraps",
        verbs=dict(Move=True, Battle=False, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Attach", "Stick a free remora onto one warrior of another faction in the same reef. It goes wherever that warrior goes and can't be hit. If that warrior is removed, the remora drops off in its reef."),
            ("Tiny", "Remoras never count for rule. Free remoras move ignoring rule."),
            ("Company", "Remoras only play in games with at least two other factions."),
        ],
        pieces="10 remoras.",
        setup="3 remoras at any gate.",
        turn=[("Dawn", "1 remora arrives at any gate."), ("Day", "3 actions: swim (move free remoras one channel), attach, or let go."), ("Dusk", "Score your rides.")],
        scores=["1 VP each time a faction you ride removes pieces of a faction other than yours, once per attack.", "At Dusk, 1 VP per faction you ride."],
        note="A remora's front fin has become a suction disc. It rides sharks, turtles and whales, and eats their leftovers.",
    ),
    dict(
        key="crabs", name="Hermit Crabs", role="Merchant", glyph="hermit", color="hermit",
        latin="Dardanus", common="hermit crabs",
        complexity=3, reach=4,
        tagline="Every shell has a price.",
        idea="The reef's only shell shop. Other factions buy shells to protect their warriors, and every card they pay becomes an action for the crabs.",
        core="Sell armor; spend the payments as actions",
        scores_by="selling shells",
        verbs=dict(Move=True, Battle=False, Build=True, Recruit=True, Craft=True),
        rules=[
            ("Shell shop", "At the start of another faction's Day, it may buy shells from your pool at your price, paying in cards. Each shell goes in a reef where the buyer has pieces and ignores the next hit on the buyer's pieces there, then comes back to your pool. Shells can't be hit or taken."),
            ("Till", "Cards paid to you go into your Till, and at Dawn you may add cards from your hand. Every Day action costs one Till card."),
            ("Own shells", "Your crabs can wear shells from your pool for free, the same way."),
        ],
        pieces="12 crabs. 4 markets (buildings). 8 shells.",
        setup="1 market and 4 crabs in any reef.",
        turn=[("Dawn", "Add cards to your Till."), ("Day", "Actions, one Till card each: move, recruit (2 crabs at a market), or build a market in a reef you rule (the Till card must match the reef's suit)."), ("Dusk", "Set your shell price: 1 to 3 cards.")],
        scores=["1 VP per shell sold. Markets score when built: 1, 2, 3, 4.", "Gear crafted at your markets."],
        note="When a big empty shell turns up, hermit crabs line up by size and swap homes down the line. Biologists call it a vacancy chain.",
    ),
    dict(
        key="anglers", name="Anglerfish", role="Trapper", glyph="abyss", color="angler",
        latin="Ceratias holboelli", common="deep-sea anglerfish",
        complexity=3, reach=5,
        tagline="Follow the light.",
        idea="They live in the Trench and never swim the reef. They hang lures with real rewards over it and eat whoever stays too long.",
        core="Hang lures with real rewards; eat whoever stays",
        scores_by="eating at lures",
        verbs=dict(Move=False, Battle=True, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Lures", "At Dawn, place or move your lures in rim reefs or their neighbors and pick each lure's offer. <i>Treasure</i>: a faction that moves warriors here draws a card, once per turn. <i>Shelter</i>: warriors here can't be battled except by you. <i>Glory</i>: a faction that rules this reef at the end of its turn scores 1 VP. You start with 2 lures; the 3rd and 4th unlock after your snaps remove 4 and 8 warriors."),
            ("Snap", "At Dusk, at each lure with enemy warriors, you may bring up to 3 anglers out of the Trench and battle one faction there. You deal 2 hits before the dice, like an Ambush. Survivors sink back into the Trench."),
            ("The Trench", "Your anglers live in the Trench, off the map, where nothing can attack them."),
        ],
        pieces="10 anglers. 4 lures (tokens).",
        setup="3 anglers in the Trench and 2 lures.",
        turn=[("Dawn", "Set your lures."), ("Day", "Discard up to 2 cards: each adds 2 anglers to the Trench."), ("Dusk", "Snap.")],
        scores=["1 VP per enemy warrior your snaps remove."],
        note="An anglerfish's lure is a fin spine tipped with glowing bacteria. Prey swims toward the light and into the mouth.",
    ),
    dict(
        key="octopus", name="Octopus", role="Programmer", glyph="octo", color="octo",
        latin="Enteroctopus dofleini", common="giant Pacific octopus",
        complexity=4, reach=7,
        tagline="Eight arms. Eight opinions.",
        idea="One giant octopus whose eight arms think for themselves. Each arm keeps a standing order and carries it out every turn, in number order, whether or not it still makes sense. Whatever the arms steal goes into the octopus's garden.",
        core="Program eight arms; steal treasures for the garden",
        scores_by="its garden",
        verbs=dict(Move=True, Battle=True, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Orders", "At Dawn, put 1 or 2 cards under arms with no order. The suit is the order. Kelp: <i>reach</i>, move that arm one channel, ignoring rule. Sponge: <i>grab</i>, battle in its reef. Pearl: <i>steal</i>, take an enemy token from its reef, or a random card from a faction with pieces there, into your garden. Moon: pick each time."),
            ("Recoil", "Arms act from 1 to 8. If an arm can't carry out its order, discard the orders on it and on every higher arm, and lose 1 VP per order discarded."),
            ("Reach", "Arms stay within 2 channels of the Mantle, your body. After the Mantle moves, arms out of reach snap back to it. Lost arms regrow at the Mantle at Dusk."),
        ],
        pieces="The Mantle and 8 numbered arms, all warriors.",
        setup="The Mantle and all 8 arms in one reef that isn't a gate.",
        turn=[("Dawn", "Add orders."), ("Day", "Arms act, 1 to 8. Then the Mantle may move to any reef with one of your arms."), ("Dusk", "Regrow arms, then score the garden.")],
        scores=["Each stolen token scores the usual 1 VP.", "At Dusk, 1 VP per kind of treasure in your garden: each token type, and each suit of stolen card. If the Mantle is removed, the garden is lost and the Mantle returns at Dawn beside any arm."],
        note="About two thirds of an octopus's neurons are in its arms. Each arm can taste and grab on its own.",
    ),
    dict(
        key="cuttlefish", name="Cuttlefish", role="Illusionist", glyph="court", color="court",
        latin="Sepia apama", common="giant cuttlefish",
        complexity=4, reach=3,
        tagline="Trust your eyes. Everyone else does.",
        idea="Cuttlefish change color in a heartbeat. They repaint reefs to a new suit for every faction, and score by painting patterns across the map.",
        core="Repaint the suits of reefs; score patterns",
        scores_by="patterns on the map",
        verbs=dict(Move=True, Battle=False, Build=False, Recruit=True, Craft=False),
        rules=[
            ("Paint", "Discard a card to put a pigment of its suit (Moon: any suit) in a reef with your cuttlefish, or to move one of your pigments there. That reef is that suit for every faction and every rule."),
            ("Camouflage", "To battle your cuttlefish in a painted reef, a faction must first discard a card of that reef's suit."),
            ("Galleries", "Three face-up Gallery cards each show a pattern, such as three connected reefs of one suit. At Dusk, score every pattern on the map that has your cuttlefish in each of its reefs, then replace it."),
        ],
        pieces="12 cuttlefish. 6 pigments (tokens, 2 per suit).",
        setup="3 cuttlefish in each of two reefs.",
        turn=[("Dawn", "1 cuttlefish arrives in each reef with your pigment."), ("Day", "3 actions: move or paint."), ("Dusk", "Score galleries.")],
        scores=["Each pattern scores 2 to 4 VP."],
        note="Cuttlefish can change color in under a second, using millions of pigment cells in their skin.",
    ),
]

# (attacker, target, why). Rendered as "Hurts" on the first plate and "Hurt by" on the second,
# so the two sides can never disagree.
PRESSURE = [
    ("Sharks", "Sardines", "they can't fight back, and every fight leaves Blood"),
    ("Sharks", "Parrotfish", "two hits per shark"),
    ("Sharks", "Sea Snake", "one hit mid-body cuts it"),
    ("Sharks", "Octopus", "two hits per shark against lone arms"),
    ("Jellyfish", "Sharks", "forced moves swim into stings"),
    ("Jellyfish", "Sardines", "every crossing risks a sting"),
    ("Jellyfish", "Lionfish", "stings as they spread"),
    ("Jellyfish", "Sea Snake", "the Head slithers into stings"),
    ("Lionfish", "Sharks", "venom hits attackers first"),
    ("Lionfish", "Parrotfish", "gorging, and venom against their battles"),
    ("Lionfish", "Octopus", "lone arms are easy to outnumber"),
    ("Lionfish", "Sea Snake", "a gorge mid-body cuts it"),
    ("Lionfish", "Remoras", "free remoras are easy to outnumber"),
    ("Starfish", "Coral", "Rubble takes building slots"),
    ("Starfish", "Hermit Crabs", "Rubble takes market slots"),
    ("Starfish", "Sea Turtles", "Rubble takes nest slots"),
    ("Parrotfish", "Coral", "grazing eats coral"),
    ("Parrotfish", "Starfish", "grazing eats Rubble"),
    ("Parrotfish", "Hermit Crabs", "grazing eats markets"),
    ("Parrotfish", "Sea Turtles", "grazing eats nests and eggs"),
    ("Parrotfish", "Cuttlefish", "grazing eats pigments"),
    ("Parrotfish", "Jellyfish", "sandbars stop drift"),
    ("Parrotfish", "Sea Snake", "sandbars block its path"),
    ("Octopus", "Starfish", "arms steal Rubble"),
    ("Octopus", "Sea Turtles", "arms steal eggs"),
    ("Octopus", "Anglerfish", "arms steal lures"),
    ("Octopus", "Cuttlefish", "arms steal pigments"),
    ("Cuttlefish", "Coral", "repainting changes what grows and spawns"),
    ("Cuttlefish", "Jellyfish", "repainting changes where blooms land"),
    ("Cuttlefish", "Sea Turtles", "repainting changes what turtles can eat"),
    ("Anglerfish", "Sardines", "schools keep crossing the lure reefs"),
    ("Sea Snake", "Sardines", "big schools make it grow fast"),
]

LINEUPS = [
    ("First blood", ["Sharks", "Coral"]),
    ("Reef and ruin", ["Parrotfish", "Starfish", "Coral"]),
    ("Hitchhikers", ["Sharks", "Sea Turtles", "Remoras", "Lionfish"]),
    ("Strange waters", ["Octopus", "Cuttlefish", "Jellyfish", "Anglerfish"]),
]

CHANGES_SHARED = [
    ("Pieces", "Draft 1 never defined a token. Blood had no owner, and sandbars and current arrows sat on channels where no rule could reach them.", "Four kinds of piece, each with fixed rules. Blood is neutral, and channel markers can't be hit."),
    ("Crafting", "Every faction could craft items, but no plate said what it crafts with.", "Only buildings craft, so only the three factions that build can craft."),
    ("Hits", "Stings, snaps, bait balls and grazing removed pieces with no shared definition, and Blood only counted battles.", "An attack is anything that deals hits. Blood and remora scraps follow any attack."),
    ("Card draw", "Some plates repeated “draw a card at Dusk” and others didn't.", "Everyone draws 1 card at Dusk. Plates list only extra draws."),
    ("Undefined words", "Jellyfish used “round” and anglerfish used “two channels from the Trench”, but neither was defined.", "A round is defined, and the Trench touches exactly the three rim reefs."),
    ("Suit names", "Coral was both a card suit and a faction.", "The suit is now Sponge."),
    ("Reach", "Reach claimed to measure how much map a faction holds, but the anglerfish hold none, and quiet lineups with no fighting were allowed.", "Reach now measures pressure on other factions, with new minimums per lineup."),
]

CHANGES_FACTIONS = [
    ("Sharks", "“No homes”, yet they built Nurseries. Cards “paid for actions”, yet they also had 3 free ones. One rule scored only against the Shoal.", "No buildings: new sharks arrive from the open ocean. Cards buy one extra action, like every faction with a fixed number of actions."),
    ("Sardines", "Merged schools lost track of the gate they came in by. Their plate said they love the jellyfish's currents while the food web said jellyfish hunt them.", "The game tracks every fish. Every relationship now comes from a rule."),
    ("Coral", "Three coral types, Calcium, bleaching, spawning, battles and two scoring tracks. Its stinging cells copied the jellyfish sting.", "One coral type. It grows and spawns and never starts a fight."),
    ("Jellyfish", "“You never command them”, yet they could battle and build stalks. Returning jellyfish needed stalks that might not exist.", "They bend currents and bloom, nothing else."),
    ("Parrotfish", "Sand came from ruling reefs, not from chewing. Cays blocked everyone without saying who rules them. A cocoon rule added a fifth system.", "Sand comes only from grazing, and islands belong to the parrotfish."),
    ("Hermit Crabs", "Shells were armor for sale, the crabs' own armor and a conveyor line at once. “Each market moves the line” contradicted “the line moves when a large shell arrives.” They sold items they had no way to make.", "One pool of shells that goes out and comes back."),
    ("Anglerfish", "Lure count was “your Glow”, but an upgrade also gave +1 lure, and Glow was spent as money too. Their Gullet clashed with jellyfish immortality. “Fish and sharks chase light” had no rule behind it.", "No Glow. Extra lures unlock by eating."),
    ("Siphon Chain", "“Loose pieces touching the chain” and “walls off the middle” had no rules behind them, on top of four zooid types.", "Now the Sea Snake: the same one-body idea with an animal everyone knows, in three rules."),
    ("Octopus", "Arms stole items from reefs, but items live on faction boards, and sandbars, which sit on channels. Dens, ink, regrowth, garden and recoil all ran at once.", "Arms steal tokens and cards. Dens and ink are gone."),
    ("Cuttlefish", "Pigments were placed at both Dawn and Day, across six systems: paint, camouflage, hypnosis, sneakers, salons and galleries.", "Paint, camouflage and galleries."),
    ("Food web", "It said every faction presses on at least two others, but coral pressed on none. Several claims had no rule behind them.", "Every “hurts” is generated from a rule and appears as a matching “hurt by” on the other plate."),
]

SHARED_RULES = [
    ("Twelve reefs", "Each reef has a suit (Kelp, Sponge or Pearl) and one to three slots for buildings. Channels connect neighboring reefs.", False),
    ("Edges of the map", "The four corner reefs are gates to the open ocean. The top row is the shore. The three reefs touching the Trench are the rim.", True),
    ("A turn", "Dawn, Day, Dusk. At Dusk every faction draws a card and discards down to five. A round is one turn for every faction.", False),
    ("Pieces", "<b>Warriors</b> fight and count for rule. <b>Buildings</b> fill slots and count for rule. <b>Tokens</b> sit in a reef and don't count. <b>Channel markers</b> sit on channels, one per channel, and can't be hit.", False),
    ("Rule and moving", "You rule a reef when you have the most warriors and buildings there; a tie means nobody does. A move takes any number of your warriors to a neighboring reef, and you must rule where you leave or where you arrive. Placing pieces from your supply isn't moving.", False),
    ("Currents", "Moving along a current never needs rule. Four currents circle the middle of the map as the Gyre.", True),
    ("Attacks and hits", "An attack is a battle or any ability that deals hits. Each hit removes one of the target's pieces in that reef, warriors first, and its owner picks which. Removing an enemy building or token scores 1 VP. Removing warriors scores nothing unless a plate says so.", False),
    ("Battle", "Pick a reef with your warriors and a faction with pieces there. Roll two dice showing 0 to 3: you deal the higher, the defender deals the lower, each capped by its warriors there. A defender with no warriors takes 1 extra hit. An Ambush card of the reef's suit deals 2 hits before the roll.", False),
    ("Cards", "54 cards in Kelp, Sponge, Pearl and wild Moon. Once per turn, a faction with a fixed number of Day actions may discard a card for one more.", False),
    ("Gear", "Each building can craft once per turn, paying with its reef's suit. Gear scores points and gives a small lasting bonus. Factions without buildings can't craft.", False),
    ("Winning", "First to 30 VP. Once you have 10 VP you may play a Dominance card instead: you stop scoring, and you win if you rule three reefs of its suit at the start of your turn (Moon: two opposite gates).", False),
]

PHASES = [
    ("Engine and first pair.", "Map, cards, rule, moving, attacks, crafting, Dominance, saving and pass-and-play. Sharks and Coral with bots. First playable APK."),
    ("Moving and hitting.", "Sardines, Lionfish, Starfish and Jellyfish, with bots."),
    ("Shaping and riding.", "Parrotfish, Sea Turtles, Sea Snake and Remoras, with bots."),
    ("The schemers.", "Hermit Crabs, Anglerfish, Octopus and Cuttlefish, with bots."),
    ("Balance.", "A simulator plays thousands of bot-vs-bot games to find factions that win too often, and I tune the numbers."),
]

REEFS = [
    # key, name, suit, x, y, slots, gate, label above
    ("gull", "Gull Rock", "p", 90, 120, 1, True, False),
    ("flats", "Flats", "s", 300, 110, 2, False, False),
    ("kelpwood", "Kelp Wood", "k", 510, 130, 2, False, False),
    ("wreck", "Shipwreck", "p", 710, 120, 1, True, False),
    ("garden", "Garden", "s", 110, 290, 2, False, False),
    ("hollow", "Hollow", "s", 310, 270, 3, False, True),
    ("arch", "Arch", "k", 500, 290, 3, False, True),
    ("meadow", "Meadow", "k", 700, 280, 2, False, False),
    ("drift", "Driftwood", "k", 90, 460, 1, True, False),
    ("oyster", "Oyster Beds", "p", 290, 440, 2, False, False),
    ("bluehole", "Blue Hole", "p", 500, 450, 2, False, False),
    ("shelf", "Shelf", "s", 710, 460, 1, True, False),
]
CHANNELS = [
    ("gull", "flats"), ("flats", "kelpwood"), ("kelpwood", "wreck"), ("gull", "garden"),
    ("flats", "hollow"), ("kelpwood", "arch"), ("wreck", "meadow"), ("garden", "hollow"),
    ("arch", "meadow"), ("garden", "drift"), ("meadow", "shelf"), ("drift", "oyster"),
    ("bluehole", "shelf"), ("gull", "hollow"), ("meadow", "bluehole"),
]
GYRE = [("hollow", "arch"), ("arch", "bluehole"), ("bluehole", "oyster"), ("oyster", "hollow")]
SHORE = ["gull", "flats", "kelpwood", "wreck"]
RIM = ["oyster", "bluehole", "shelf"]
SUIT_NAME = {"k": "KELP", "s": "SPONGE", "p": "PEARL"}


def check():
    names = [f["name"] for f in FACTIONS]
    errors = []
    for f in FACTIONS:
        n = f["name"]
        if len(f["rules"]) != 3:
            errors.append(f"{n}: has {len(f['rules'])} special rules, every faction has exactly 3")
        if f["verbs"]["Craft"] != f["verbs"]["Build"]:
            errors.append(f"{n}: only buildings craft, so Craft must equal Build")
        if len(f["scores"]) > 2:
            errors.append(f"{n}: scores in more than 2 ways")
        if [p for p, _ in f["turn"]] != ["Dawn", "Day", "Dusk"]:
            errors.append(f"{n}: turn must be Dawn, Day, Dusk")
        text = " ".join([t for _, t in f["rules"]] + [t for _, t in f["turn"]] + f["scores"] + [f["pieces"], f["setup"]])
        for other in names:
            if other != n and re.search(rf"\b{re.escape(other)}\b", text):
                errors.append(f"{n}: a rule names another faction ({other})")
        if "draw a card" in " ".join(t for p, t in f["turn"] if p == "Dusk").lower():
            errors.append(f"{n}: repeats the shared Dusk draw")
    for a, b, _ in PRESSURE:
        for x in (a, b):
            if x not in names:
                errors.append(f"pressure edge names unknown faction {x}")
    order = [f["complexity"] for f in FACTIONS]
    if order != sorted(order):
        errors.append("plates must be ordered from easiest to hardest")
    reach = {f["name"]: f["reach"] for f in FACTIONS}
    for title, lineup in LINEUPS:
        total = sum(reach[x] for x in lineup)
        if total < RULE_THRESHOLDS[len(lineup)]:
            errors.append(f"lineup {title} has reach {total}, below {RULE_THRESHOLDS[len(lineup)]}")
        if "Remoras" in lineup and len(lineup) < 3:
            errors.append(f"lineup {title} puts Remoras in a 2-faction game")
    if errors:
        sys.exit("Design is inconsistent:\n  " + "\n  ".join(errors))


def esc(s):
    return s.replace("&", "&amp;")


def star_path(cx, cy, arms, r_out, r_in):
    pts = []
    for i in range(arms * 2):
        r = r_out if i % 2 == 0 else r_in
        a = -math.pi / 2 + i * math.pi / arms
        pts.append(f"{cx + r * math.cos(a):.1f} {cy + r * math.sin(a):.1f}")
    return "M" + " L".join(pts) + " Z"


GLYPHS = {
    "shark": '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 34 C17 25 33 23 47 28 L58 20 L55 32 L58 44 L47 37 C33 42 17 42 5 34 Z"/><path d="M25 26 L31 13 L36 25"/><path d="M34 39 L38 46 L41 38"/><path d="M19 31 v6 M22.5 30.5 v6.5"/></g><circle cx="12" cy="32" r="1.6" fill="currentColor"/>',
    "shoal": '<g fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><path d="M8 18 C12 13 20 13 24 18 C20 23 12 23 8 18 Z M24 18 L29 14 L29 22 Z"/><path d="M30 30 C34 25 42 25 46 30 C42 35 34 35 30 30 Z M46 30 L51 26 L51 34 Z"/><path d="M10 42 C14 37 22 37 26 42 C22 47 14 47 10 42 Z M26 42 L31 38 L31 46 Z"/><path d="M32 51 C35 47 41 47 44 51 C41 55 35 55 32 51 Z M44 51 L48 48 L48 54 Z"/><path d="M34 10 C37 6 43 6 46 10 C43 14 37 14 34 10 Z M46 10 L50 7 L50 13 Z"/></g>',
    "lion": '<g fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 36 C18 28 36 27 45 33 L56 27 L56 43 L45 38 C36 44 18 44 12 36 Z"/><path d="M20 30 L15 12 M25 29 L23 8 M31 28 L32 8 M37 29 L41 11 M42 31 L49 16"/><path d="M24 39 L14 54 M28 40 L22 58 M32 40 L31 58"/><path d="M29 30 v11 M35 31 v10"/></g><circle cx="17" cy="34" r="1.6" fill="currentColor"/>',
    "star": '<path d="' + star_path(32, 33, 10, 27, 13) + '" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linejoin="round"/><circle cx="32" cy="33" r="5" fill="none" stroke="currentColor" stroke-width="2"/>',
    "coral": '<g fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M16 58 H48"/><path d="M32 58 V36"/><path d="M32 44 L20 32 V18"/><path d="M20 30 L12 24 V16"/><path d="M32 38 L44 26 V10"/><path d="M44 24 L53 18 V12"/><path d="M32 36 V24"/><path d="M20 22 L25 16"/></g>',
    "jelly": '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M13 32 C13 12 51 12 51 32 C45 29 39 34 32 31 C25 34 19 29 13 32 Z"/><path d="M19 34 c-3 6 3 9 0 15 c-2 4 1 7 0 9"/><path d="M28 34 c-3 7 3 10 0 17"/><path d="M36 34 c3 7 -3 10 0 17"/><path d="M45 34 c3 6 -3 9 0 15 c2 4 -1 7 0 9"/></g>',
    "parrot": '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M10 32 C18 17 40 15 50 26 L58 19 L58 45 L50 38 C40 49 18 47 10 32 Z"/><path d="M10 32 L5 29 M10 32 L5 35"/><path d="M27 23 q5 9 0 18 M35 21 q5 11 0 22 M43 24 q4 8 0 16"/><path d="M8 56 h14 M28 56 h6 M40 56 h16"/></g><circle cx="17" cy="28" r="1.8" fill="currentColor"/>',
    "turtle": '<g fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="32" cy="35" rx="13" ry="16"/><circle cx="32" cy="13" r="4.5"/><path d="M20 27 C11 20 5 23 7 28 C11 29 16 30 20 32"/><path d="M44 27 C53 20 59 23 57 28 C53 29 48 30 44 32"/><path d="M22 45 C16 50 15 55 18 56 C21 54 24 51 25 49"/><path d="M42 45 C48 50 49 55 46 56 C43 54 40 51 39 49"/><path d="M32 27 l6 4 v7 l-6 4 l-6 -4 v-7 z"/></g>',
    "snake": '<path d="M6 46 C12 34 20 33 26 42 C32 51 40 51 46 40 C50 32 54 29 57 30" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round"/><path d="M6 46 C12 34 20 33 26 42 C32 51 40 51 46 40 C50 32 54 29 57 30" fill="none" style="stroke:var(--surface)" stroke-width="6.5" stroke-dasharray="3 6"/><circle cx="57" cy="30" r="4.5" fill="currentColor"/>',
    "remora": '<g fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M6 36 C14 29 40 29 50 33 L58 28 L58 42 L50 38 C40 42 14 42 6 36 Z"/><ellipse cx="19" cy="30" rx="8" ry="2.6"/><path d="M14 29 v2 M17 28.5 v3 M20 28.5 v3 M23 29 v2"/><path d="M14 37 H46"/></g><circle cx="10" cy="35" r="1.5" fill="currentColor"/>',
    "hermit": '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M40 30 m0 -4 a4 4 0 1 1 -4 4 a8 8 0 1 1 8 8 a12 12 0 1 1 -12 -12 a16 16 0 0 1 20 10"/><path d="M26 40 C14 42 9 34 14 29 C16 33 20 34 24 33"/><path d="M22 26 l-4 -9 M26 25 l-1 -10"/><path d="M24 46 l-7 8 M30 48 l-4 9 M36 49 l0 8"/></g><circle cx="18" cy="16" r="1.8" fill="currentColor"/><circle cx="25" cy="14" r="1.8" fill="currentColor"/>',
    "abyss": '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 38 C12 22 42 18 50 31 L58 25 L58 46 L50 41 C43 54 12 54 12 38 Z"/><path d="M13 42 l4 -4 l3 4 l3 -4 l3 4 l3 -4 l3 4"/><path d="M25 25 C24 12 36 6 42 12"/></g><circle cx="42" cy="15" r="4" style="fill:var(--glow)"/><circle cx="21" cy="32" r="1.8" fill="currentColor"/>',
    "octo": '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M32 6 C45 6 49 21 45 29 C42 33 22 33 19 29 C15 21 19 6 32 6 Z"/><path d="M21 31 c-4 8 -12 9 -15 4"/><path d="M26 33 c-2 10 -8 17 -13 16 c-3 -1 -2 -5 1 -4"/><path d="M32 33 c0 10 -2 18 -6 22"/><path d="M38 33 c2 10 8 17 13 16 c3 -1 2 -5 -1 -4"/><path d="M43 31 c4 8 12 9 15 4"/></g><circle cx="27" cy="22" r="1.8" fill="currentColor"/><circle cx="37" cy="22" r="1.8" fill="currentColor"/>',
    "court": '<g fill="none" stroke="url(#chroma)" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M8 32 C8 21 40 19 50 32 C40 45 8 43 8 32 Z"/><path d="M9 25 q4 -5 8 -1 q4 -5 8 -1 q4 -5 8 -1 q4 -4 8 0"/><path d="M9 39 q4 5 8 1 q4 5 8 1 q4 5 8 1 q4 4 8 0"/><path d="M50 32 l8 -5 M50 32 h9 M50 32 l8 5"/><path d="M20 26 v12 M27 25 v14 M34 26 v12"/><path d="M40 30 q2 2 4 0"/></g>',
}


def glyph(f, cls=""):
    style = "" if f["color"] == "court" else f' style="color:var(--f-{f["color"]})"'
    return f'<svg class="{cls}"{style} aria-hidden="true"><use href="#g-{f["glyph"]}"/></svg>'


def roman(n):
    vals = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
    out = ""
    for v, s in vals:
        while n >= v:
            out += s
            n -= v
    return out


def pips(n):
    return f'<span class="pips" aria-label="{n} of 4">' + "".join(f'<i class="{"on" if i < n else ""}"></i>' for i in range(4)) + "</span>"


def verb_chips(f):
    out = []
    for v in VERBS:
        on = f["verbs"][v]
        out.append(f'<span class="{"on" if on else "off"}">{v}<span class="vh">{"" if on else " (never)"}</span></span>')
    return '<p class="verbs">' + "".join(out) + "</p>"


def pressure_lines(f):
    n = f["name"]
    hurts = [(b, why) for a, b, why in PRESSURE if a == n]
    hurt_by = [(a, why) for a, b, why in PRESSURE if b == n]

    def fmt(items):
        if not items:
            return "nobody directly"
        return "; ".join(f"{x} ({why})" for x, why in items)

    return f'<p><b>Hurts</b>{fmt(hurts)}.</p><p><b>Hurt by</b>{fmt(hurt_by)}.</p>'


def plate(i, f):
    rules = "".join(f"<li><b>{t}.</b> {esc(d)}</li>" for t, d in f["rules"])
    turn = "".join(f"<dt>{p}</dt><dd>{esc(d)}</dd>" for p, d in f["turn"])
    scores = "".join(f"<li>{esc(s)}</li>" for s in f["scores"])
    return f'''
  <article class="plate" id="{f["key"]}" style="--fc:var(--f-{f["color"] if f["color"] != "court" else "court-a"})">
    <div class="plate-head">
      <div class="specimen">{glyph(f)}</div>
      <div class="plate-title"><p class="cap">Plate {roman(i)} · {f["role"]}</p><h3>{f["name"]}</h3><p class="taxon"><i>{f["latin"]}</i> · {f["common"]}</p></div>
      <dl class="stats"><dt>Complexity</dt><dd>{pips(f["complexity"])}</dd><dt>Reach</dt><dd>{f["reach"]}</dd></dl>
    </div>
    <p class="tagline">“{f["tagline"]}”</p>
    <p class="idea">{esc(f["idea"])}</p>
    {verb_chips(f)}
    <div class="plate-body">
      <div>
        <h4>Its three rules</h4>
        <ul class="mech">{rules}</ul>
      </div>
      <div class="side">
        <div><h4>Pieces</h4><p>{esc(f["pieces"])} <span class="muted">Setup: {esc(f["setup"])}</span></p></div>
        <div><h4>A turn</h4><dl class="turn">{turn}</dl></div>
        <div><h4>Scores</h4><ul>{scores}</ul></div>
      </div>
    </div>
    <div class="plate-foot">
      <p><b>Field note</b>{esc(f["note"])}</p>
      <div class="press">{pressure_lines(f)}</div>
    </div>
  </article>'''


def glance_table():
    head = "".join(f"<th scope=\"col\" class=\"v\">{v}</th>" for v in VERBS)
    rows = []
    for i, f in enumerate(FACTIONS, 1):
        cells = "".join(
            f'<td class="v">{"<span class=dot aria-label=yes></span>" if f["verbs"][v] else "<span class=dash aria-label=never></span>"}</td>'
            for v in VERBS
        )
        rows.append(
            f'<tr><td class="num">{roman(i)}</td><th scope="row"><a href="#{f["key"]}">{glyph(f, "mini")}<span>{f["name"]}</span></a></th>'
            f'<td class="core">{esc(f["core"])}</td>{cells}<td class="by">{esc(f["scores_by"])}</td></tr>'
        )
    return f'''<div class="table-scroll"><table class="glance">
      <thead><tr><th scope="col" class="num">Plate</th><th scope="col">Faction</th><th scope="col">Core idea</th>{head}<th scope="col">Scores by</th></tr></thead>
      <tbody>{"".join(rows)}</tbody></table></div>'''


def map_svg():
    pos = {r[0]: (r[3], r[4]) for r in REEFS}
    gate = {r[0]: r[6] for r in REEFS}
    out = ['<svg viewBox="0 0 800 580" role="img" aria-label="Twelve reefs connected by channels. The shore runs along the top, the Trench along the bottom, and four one-way currents circle the middle.">',
           '<defs><marker id="arr" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path class="arrowhead" d="M0 0 L10 5 L0 10 Z"/></marker></defs>',
           '<rect class="shore" x="20" y="8" width="760" height="32" rx="6"/><text class="band-label shore-label" x="400" y="29">THE SHORE</text>']
    for k in SHORE:
        x, y = pos[k]
        top = y - (42 if gate[k] else 34)
        out.append(f'<line class="rim" x1="{x}" y1="40" x2="{x}" y2="{top}"/>')
    for a, b in CHANNELS:
        (x1, y1), (x2, y2) = pos[a], pos[b]
        out.append(f'<line class="edge" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}"/>')
    for a, b in GYRE:
        (x1, y1), (x2, y2) = pos[a], pos[b]
        d = math.hypot(x2 - x1, y2 - y1)
        ux, uy = (x2 - x1) / d, (y2 - y1) / d
        out.append(f'<line class="curr" x1="{x1 + ux * 38:.0f}" y1="{y1 + uy * 38:.0f}" x2="{x2 - ux * 40:.0f}" y2="{y2 - uy * 40:.0f}" marker-end="url(#arr)"/>')
    out.append('<text class="gyre-label" x="400" y="368">the Gyre</text>')
    labels = {}
    for key, name, suit, x, y, slots, is_gate, above in REEFS:
        ly = y - 41 if above else y + (60 if is_gate else 52)
        labels[key] = ly
    for k in RIM:
        x, _ = pos[k]
        out.append(f'<line class="rim" x1="{x}" y1="{labels[k] + 10}" x2="{x}" y2="536"/>')
    out.append('<rect class="trench" x="230" y="536" width="540" height="38" rx="6"/><text class="band-label trench-label" x="500" y="560">THE TRENCH</text>')
    for key, name, suit, x, y, slots, is_gate, above in REEFS:
        g = [f'<g class="reef {suit}">']
        if is_gate:
            g.append(f'<circle class="gate" cx="{x}" cy="{y}" r="42"/>')
        g.append(f'<circle class="under" cx="{x}" cy="{y}" r="34"/><circle class="body" cx="{x}" cy="{y}" r="34"/>')
        g.append(f'<text class="suit" x="{x}" y="{y - 6}">{SUIT_NAME[suit]}</text>')
        start = x - (slots * 13 - 4) / 2
        for s in range(slots):
            g.append(f'<rect class="nook" x="{start + s * 13:.1f}" y="{y + 2}" width="9" height="9"/>')
        g.append(f'<text class="name" x="{x}" y="{labels[key]}">{name}</text></g>')
        out.append("".join(g))
    out.append("</svg>")
    return "\n".join(out)


def principles():
    never_battle = sum(1 for f in FACTIONS if not f["verbs"]["Battle"])
    never_build = sum(1 for f in FACTIONS if not f["verbs"]["Build"])
    items = [
        ("One idea.", "Every faction is built around a single core idea and has exactly three special rules."),
        ("Only what it needs.", f"{never_battle} of the {len(FACTIONS)} factions never start a battle, and {never_build} never build. The table below shows what each one does and never does."),
        ("Shared words.", "Every rule is written with the same words: warrior, building, token, channel marker, attack, hit, rule. A sting is a hit, so a turtle's shell ignores it without a special case."),
        ("No names.", "No rule mentions another faction. Every interaction comes from the shared words."),
        ("Both sides agree.", "Every “hurts” on one plate appears as “hurt by” on the other."),
        ("Always a way to win.", "Each faction scores in one or two ways, both from its core idea, and can reach 30 VP in any lineup it's allowed in."),
    ]
    return "".join(f"<li><b>{t}</b> {d}</li>" for t, d in items)


def lineups_html():
    reach = {f["name"]: f["reach"] for f in FACTIONS}
    cards = []
    for title, lineup in LINEUPS:
        rows = "".join(f"<li><span>{x}</span><span>{reach[x]}</span></li>" for x in lineup)
        total = sum(reach[x] for x in lineup)
        cards.append(f'<div class="lineup"><p class="cap">{len(lineup)} factions · needs {RULE_THRESHOLDS[len(lineup)]}</p><h3>{title}</h3><ul>{rows}</ul><p class="sum"><span>Reach</span><span>{total}</span></p></div>')
    return "".join(cards)


def changes_html(items):
    return "".join(f'<li><b>{t}</b><p><span class="was">Draft 1:</span> {esc(w)}</p><p><span class="now">Now:</span> {esc(n)}</p></li>' for t, w, n in items)


def build(path):
    check()
    symbols = "".join(f'<symbol id="g-{k}" viewBox="0 0 64 64">{v}</symbol>' for k, v in GLYPHS.items())
    plates = "".join(plate(i, f) for i, f in enumerate(FACTIONS, 1))
    rules = "".join(f'<div class="rule"><h3>{t}{"<span class=new>reef</span>" if new else ""}</h3><p>{d}</p></div>' for t, d, new in SHARED_RULES)
    phases = "".join(f"<li><div><b>{t}</b> {d}</div></li>" for t, d in PHASES)
    html = TEMPLATE
    for k, v in {
        "%%SYMBOLS%%": symbols, "%%MAP%%": map_svg(), "%%GLANCE%%": glance_table(), "%%PLATES%%": plates,
        "%%RULES%%": rules, "%%PHASES%%": phases, "%%PRINCIPLES%%": principles(), "%%LINEUPS%%": lineups_html(),
        "%%CHANGES_SHARED%%": changes_html(CHANGES_SHARED), "%%CHANGES_FACTIONS%%": changes_html(CHANGES_FACTIONS),
        "%%COUNT%%": str(len(FACTIONS)),
    }.items():
        html = html.replace(k, v)
    Path(path).write_text(html, encoding="utf-8")


def export_plates(path):
    """Writes the plate text the app shows in its rules screen, so the app and this page can't drift apart."""
    import json
    strip = lambda t: re.sub(r"</?i>", "", t)
    plates = [
        dict(key=f["key"], name=f["name"], role=f["role"], tagline=f["tagline"], idea=f["idea"],
             rules=[dict(title=t, text=strip(d)) for t, d in f["rules"]],
             pieces=f["pieces"], setup=f["setup"],
             turn=[dict(phase=ph, text=strip(d)) for ph, d in f["turn"]],
             scores=f["scores"], verbs=f["verbs"], complexity=f["complexity"], reach=f["reach"])
        for f in FACTIONS
    ]
    Path(path).write_text(json.dumps(dict(plates=plates, shared=[dict(title=t, text=re.sub(r"</?b>", "", d)) for t, d, _ in SHARED_RULES]), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


TEMPLATE = Path(__file__).with_name("field_guide_template.html").read_text(encoding="utf-8")

if __name__ == "__main__":
    here = Path(__file__).parent
    build(sys.argv[1] if len(sys.argv) > 1 else here / "field-guide.html")
    export_plates(here.parent / "engine" / "src" / "main" / "resources" / "plates.json")
