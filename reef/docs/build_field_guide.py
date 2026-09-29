"""Builds field-guide.html, the Reef design document.

All faction rules live in FACTIONS below. The page is generated from this data, and
check() refuses to build when the design breaks one of its own consistency rules.

    python3 build_field_guide.py [output.html]
"""
import os
import math
import re
import sys
from pathlib import Path

VERBS = ["Move", "Battle", "Recruit", "Craft"]
RULE_THRESHOLDS = {2: 12, 3: 15, 4: 18}  # minimum total reach per faction count

FACTIONS = [
    dict(
        key="sharks", name="Sharks", role="Predator", glyph="shark", color="shark",
        latin="Carcharhinus amblyrhynchos", common="grey reef sharks",
        complexity=1, reach=9,
        tagline="Stop swimming and you drown.",
        idea="Sharks own nothing and build nothing. They roam, they fight, and every fight leaves Blood in the water. Each Blood is a meal, a frenzy that brings more sharks, or something shiny to craft with.",
        core="Must keep moving; feeds on the aftermath of every fight",
        scores_by="eating Blood",
        choice="Every Blood is 2 VP, 2 more sharks, or gear. Never more than one of them.",
        ways=dict(Move="Hunt: move, then maybe battle", Battle="Hunt, two hits per shark", Recruit="1 a Dawn, 2 per frenzy", Craft="Blood, eaten when used"),
        rules=[
            ("Keep swimming", "At Dusk, every shark that didn't move this turn drowns."),
            ("Big", "Each shark counts as two warriors whenever warriors are counted, including for rule and for the hits you can deal."),
            ("Blood in the water", "While sharks are in the game, any attack that removes a warrior leaves a Blood token in that reef, one per reef. Blood belongs to no one. Your sharks can move to a Blood reef up to two channels away, ignoring rule."),
        ],
        quirk=("Open ocean", "Sharks at a gate can swim to any other gate as one move. They dive out of the reef and turn up on the far side."),
        craft="Blood in a reef with your sharks pays that reef's suit. Crafting eats it, for no VP.",
        pieces="12 sharks. 8 Blood tokens.",
        setup="3 sharks at any gate.",
        turn=[("Dawn", "1 shark arrives at any gate, or 2 if you have none on the map."), ("Day", "3 actions: hunt. A hunt moves sharks from one reef, then they may battle where they arrive."), ("Dusk", "Each Blood in a reef with your sharks: feed on it, or frenzy (remove it and 2 sharks arrive there, as if they had moved). Then any shark that stayed still drowns.")],
        scores=["Feeding on a Blood at Dusk: 2 VP."],
        note="Many sharks breathe by swimming, pushing water over their gills. For them, stopping means suffocating.",
    ),
    dict(
        key="sardines", name="Sardines", role="Racer", glyph="shoal", color="sardine",
        latin="Sardinops sagax", common="sardines on their run",
        complexity=1, reach=4,
        tagline="Ten thousand fish, one direction.",
        idea="A race across the map. Schools come in through one gate and score by leaving through another. The bigger the school, the more it scores, but the longer it stays, the more gets eaten. A big enough school shoves everyone out of its way.",
        core="Race schools across the map and out the far side",
        scores_by="leaving through a gate",
        choice="Cash a school in now, or grow it for a bigger payout while everything hunts it.",
        ways=dict(Move="The whole school, two channels", Battle="Mob with a school of 6+", Recruit="The run, and rallies", Craft="Schools of 4 or more"),
        rules=[
            ("The run", "At Dawn, 3 sardines arrive at any gate. The game remembers the gate every fish came in by. Fish that rally count as coming in by the gate most of their school came in by."),
            ("One school", "A move takes all your sardines in a reef together, up to two channels, ignoring rule. A school of 6 or more can mob: battle, dealing at most 1 hit for every 3 fish."),
            ("Bait ball", "When battled, before any hits, you may give up the fight: lose half the school, rounded down, and move the rest to a neighboring reef. The lost fish count as removed by the attack."),
        ],
        quirk=("Wall of fish", "Right after a school of 6 or more arrives, you may push one faction that has fewer warriors there: all its warriors there move to a neighboring reef of your choice."),
        craft="Each reef with 4 or more of your sardines pays its suit.",
        pieces="30 sardines.",
        setup="5 sardines at any gate.",
        turn=[("Dawn", "The run arrives."), ("Day", "3 actions: move, mob, or rally (discard a card: 2 sardines join a school in a reef of the card's suit)."), ("Dusk", "Schools on a gate may leave the map.")],
        scores=["At Dusk, sardines on a gate they didn't come in by may leave: 1–3 fish score 1 VP, 4–6 score 2, 7–9 score 4, 10 or more score 6. They go back to your supply."],
        note="Every winter billions of sardines run up the east coast of South Africa, and the predators follow them.",
    ),
    dict(
        key="lionfish", name="Lionfish", role="Invader", glyph="lion", color="lion",
        latin="Pterois volitans", common="red lionfish",
        complexity=1, reach=8,
        tagline="Nothing here eats us.",
        idea="An invasive species with no natural enemies on this reef. Lionfish breed every turn and eat whatever they outnumber, then sit there, too stuffed to move.",
        core="Outbreed everyone and eat what you outnumber",
        scores_by="gorging and reefs held alone",
        choice="Spread out to breed everywhere, or pile up to hold reefs alone. Gorge now and sit stuffed, or keep moving.",
        ways=dict(Move="Standard moves", Battle="Gorge with no dice, or battle", Recruit="Breed, or release", Craft="Reefs you hold alone"),
        rules=[
            ("Breed", "At Dusk, each reef with 3 or more of your lionfish breeds 1 lionfish, placed there or in a neighboring reef. At Dawn, if you have fewer than 2 lionfish on the map, 2 more arrive at any gate."),
            ("Gorge", "Attack a faction whose warriors you outnumber in a reef: it takes 1 hit. No dice, and no hits back."),
            ("Venom", "A faction that battles your lionfish takes 1 hit before the dice are rolled."),
        ],
        quirk=("Stuffed", "After gorging, your lionfish in that reef can't move or gorge again for the rest of the turn. A lionfish's stomach can stretch to thirty times its size."),
        craft="Each reef where 3 or more of your lionfish are the only warriors pays its suit.",
        pieces="24 lionfish.",
        setup="2 lionfish at any gate.",
        turn=[("Dawn", "If you have fewer than 2 lionfish on the map, 2 arrive at a gate."), ("Day", "3 actions: move, battle, gorge, or release once (discard a card: 2 lionfish arrive at any gate)."), ("Dusk", "Breed, then score.")],
        scores=["1 VP for each warrior your gorging removes.", "At Dusk, after breeding, 1 VP per reef where you have 6 or more lionfish and no other faction has warriors."],
        note="Lionfish reached the Atlantic in the 1980s and spread along the whole American coast. Local fish don't recognize them as predators.",
    ),
    dict(
        key="starfish", name="Starfish", role="Outbreak", glyph="star", color="star",
        latin="Acanthaster planci", common="crown-of-thorns starfish",
        complexity=1, reach=6,
        tagline="Cut one, two grow back.",
        idea="A plague that eats reefs down to rubble. Starfish crawl toward anything built, eat it, and leave Rubble behind. Hitting them only makes more.",
        core="Eat what's built and leave Rubble; hitting them spreads them",
        scores_by="devouring and Rubble",
        choice="Eat a building for a point now, or lay Rubble that pays every Dusk. Two actions: eat here, or crawl to the next meal.",
        ways=dict(Move="Crawl, drawn to buildings", Battle="Devour, or battle", Recruit="Spawn, and regrow when hit", Craft="Your Rubble"),
        rules=[
            ("Devour", "An action in a reef with 3 or more of your starfish: remove an enemy building there, or put Rubble in an empty slot if none of your Rubble is there yet. Rubble is a token that fills its slot for everyone."),
            ("Drawn to food", "Your starfish can move into a reef with an enemy building, ignoring rule."),
            ("Spawn", "At Dawn, discard up to 2 cards. Each adds 2 starfish to a reef of its suit where you have starfish, or to any reef of its suit if you have none on the map."),
        ],
        quirk=("Cut in half", "After any attack removes your starfish, 2 starfish regrow in the neighboring reef where you have the most. Divers once chopped them up to kill them."),
        craft="Your Rubble pays its reef's suit.",
        pieces="20 starfish. 6 Rubble tokens.",
        setup="3 starfish in each of two neighboring reefs that aren't gates.",
        turn=[("Dawn", "Spawn."), ("Day", "2 actions: move, battle or devour."), ("Dusk", "Score.")],
        scores=["1 VP per building devoured, like any building removed.", "At Dusk, 1 VP for every 2 reefs that have your Rubble, rounded up."],
        note="A crown-of-thorns outbreak can kill most of the coral on a reef, and a starfish can regrow lost arms.",
    ),
    dict(
        key="coral", name="Coral", role="Builder", glyph="coral", color="coral",
        latin="Acropora cervicornis", common="staghorn coral",
        complexity=2, reach=5,
        tagline="We never move. We grow.",
        idea="A living reef. It can't take a step, but it spreads by spawning, stings whatever comes too close, and fish shelter in it. Grow tall for points, or leave room for visitors who pay you every Dusk.",
        core="Grow in place; spread only by spawning; shelter visitors",
        scores_by="growing coral and sheltering visitors",
        choice="Grow this turn, or bleach a coral for cards and grow bigger later. Crowd visitors out, or let them in for Nursery points, knowing they might wreck you.",
        ways=dict(Move="Spawn into neighbors; never moves", Battle="Polyps sting in a battle", Recruit="Spawn", Craft="Reefs with your coral"),
        rules=[
            ("Rooted", "Polyps never move, not even along currents or when pushed."),
            ("Spawn", "Once per turn, discard a card: each reef of its suit with your coral puts 1 polyp into every neighboring reef. With a Moon card, every reef with your coral spawns. If you have no coral on the map, the spawn instead puts 2 polyps into one reef of the card's suit."),
            ("Living reef", "When you are battled, each of your coral in that reef counts as a warrior for the hits you can deal."),
        ],
        quirk=("Bleach", "In a turn when you don't grow, you may take one of your coral out of the game to draw 2 cards. Stressed coral spits out its algae, turns white and dies."),
        craft="Each reef with your coral pays its suit, however many coral it has.",
        pieces="20 polyps. 15 coral (buildings).",
        setup="2 coral and 3 polyps in one reef that isn't a gate, and 1 polyp in each neighboring reef.",
        turn=[("Dawn", "Nothing."), ("Day", "3 actions: grow (discard a card of the reef's suit to build a coral in an empty slot of a reef you rule), spawn, or battle."), ("Dusk", "Score the Nursery. Draw 1 extra card per 5 coral on the map.")],
        scores=["Each coral scores when grown: 1 VP for every 4 coral on the map, at least 1.", "Nursery: at Dusk, 1 VP per reef with your coral where another faction has warriors."],
        note="Many corals spawn on the same one or two nights a year, timed by the moon. That is why a Moon card makes every reef spawn.",
    ),
    dict(
        key="jellyfish", name="Jellyfish", role="Drifter", glyph="jelly", color="jelly",
        latin="Turritopsis dohrnii", common="immortal jellyfish",
        complexity=2, reach=6,
        tagline="We can't steer. We bend the current.",
        idea="You never steer a jellyfish. They drift every turn, and all you control is the current that carries them. Whatever swims into them gets stung, and even a dead jellyfish comes back.",
        core="Drift with the currents you set; sting whatever swims in",
        scores_by="stings and big swarms",
        choice="Aim the currents at enemies to sting them, or hold your swarms still to score.",
        ways=dict(Move="Drift on currents you set", Battle="Pulse once a turn from a swarm", Recruit="Bloom, and cysts", Craft="Swarms of 3 or more"),
        rules=[
            ("Drift", "At Dusk, your jellyfish in each reef follow one current leading out of it, one channel. No current, no drift. Drifting ignores rule."),
            ("Your currents", "You own 4 current arrows. Each turns an empty channel into a one-way current, and currents work for every faction."),
            ("Sting", "When another faction moves warriors into a reef with 2 or more of your jellyfish, by any kind of movement, it takes 1 hit, which removes one of the arrivals."),
        ],
        quirk=("Immortal", "When an attack removes the last of your jellyfish in a reef, leave a cyst token there. At your Dawn, each cyst becomes 2 jellyfish."),
        craft="Each reef with 3 or more of your jellyfish pays its suit.",
        pieces="24 jellyfish. 4 current arrows (channel markers). 4 cysts (tokens).",
        setup="3 jellyfish in each of two reefs on the Gyre.",
        turn=[("Dawn", "Cysts become jellyfish."), ("Day", "Place or turn up to 2 arrows. Bloom up to twice: discard a card, and 1 jellyfish joins every reef of its suit where you have jellyfish (Moon: every such reef); with none on the map, 3 bloom in one reef of its suit. Pulse once: battle in a reef with 3 or more of your jellyfish."), ("Dusk", "Drift, then score.")],
        scores=["1 VP each time a sting removes a warrior.", "At Dusk, after drifting, 1 VP per reef with 3 or more of your jellyfish, 2 VP if it has 6 or more."],
        note="The immortal jellyfish can turn back into a young polyp when it is hurt, and grow up all over again.",
    ),
    dict(
        key="parrotfish", name="Parrotfish", role="Terraformer", glyph="parrot", color="parrot",
        latin="Bolbometopon muricatum", common="bumphead parrotfish",
        complexity=2, reach=7,
        tagline="Every beach was a reef once.",
        idea="Parrotfish chew the reef into sand and use the sand to reshape the map. Sandbars cut channels and islands rise out of the sea. At night they sleep in a bubble of their own slime.",
        core="Graze the reef into sand; reshape the map with it",
        scores_by="sandbars and islands",
        choice="Sand builds sandbars and islands, or buys gear. A cocoon keeps a reef safe but pins it for a turn.",
        ways=dict(Move="Standard moves", Battle="Standard battles", Recruit="2 a Dawn where you rule", Craft="Islands, or 3 Sand"),
        rules=[
            ("Graze", "In a reef you rule, remove one enemy building or token and gain 3 Sand. If there is nothing to eat, chew the bare reef for 2 Sand, once per reef each turn."),
            ("Sandbar", "Spend 2 Sand to put a sandbar on a channel next to a reef you rule. For everyone but you, the two reefs stop being neighbors. A faction with warriors at both ends can spend a move to dig it out."),
            ("Island", "Spend 4 Sand to raise an island in a reef you rule that touches 2 of your sandbars. You rule it for the rest of the game, your parrotfish there can't be attacked, and the island can't be removed."),
        ],
        quirk=("Pajamas", "At Dusk, you may wrap your parrotfish in one reef you rule in a slime cocoon. Until your next Dusk they can't be attacked or pushed, and they can't move."),
        craft="Your islands pay their suit, and 3 Sand pays any suit.",
        pieces="16 parrotfish. 8 sandbars (channel markers). 3 islands. 1 cocoon (token).",
        setup="4 parrotfish at any gate.",
        turn=[("Dawn", "2 parrotfish arrive in a reef you rule, or at a gate if you rule none."), ("Day", "3 actions: move, battle, graze, place a sandbar or raise an island."), ("Dusk", "Score islands. Your old cocoon opens, and you may spin a new one.")],
        scores=["1 VP per sandbar placed, 3 VP per island raised.", "At Dusk, 1 VP per island."],
        note="Much of the white sand on tropical beaches has passed through a parrotfish. Many sleep in a mucus cocoon that hides their smell.",
    ),
    dict(
        key="turtles", name="Sea Turtles", role="Wanderer", glyph="turtle", color="turtle",
        latin="Chelonia mydas", common="green sea turtles",
        complexity=2, reach=3,
        tagline="Go far. Come home.",
        idea="Four old turtles swim far out to feed, riding the currents, then come home to lay eggs on the shore. Every hatchling has to dash past whatever is waiting on the beach.",
        core="Journey out to feed, come home to nest",
        scores_by="eggs and nests",
        choice="Food makes eggs or pays for gear. Lay at the safe nest far away, or the close one with enemies on the beach.",
        ways=dict(Move="One turtle at a time, or ride a current", Battle="Standard battles, shell first", Recruit="Hatchlings become turtles", Craft="Nests, or Food"),
        rules=[
            ("Shell", "Ignore the first hit of every attack on your turtles."),
            ("Wander", "Turtles move one at a time, one channel per move, ignoring rule. A turtle can also ride a current as far as it flows, as one move."),
            ("Feed and lay", "A turtle away from the shore can feed: it takes a Food of the reef's suit, at most one of each suit. A turtle at your nest lays 1 egg per Food it carries, plus 2, using the Food up."),
        ],
        quirk=("Hatchling dash", "An egg that hatches in a reef with enemy warriors is eaten on its way to the sea: it scores you nothing, and the faction that rules that reef scores 1 VP."),
        craft="Your nests pay their reef's suit. Each Food a turtle carries pays its suit and is used up.",
        pieces="4 turtles. 3 nests (buildings). 10 eggs (tokens).",
        setup="1 nest and all 4 turtles in one shore reef.",
        turn=[("Dawn", "Every egg hatches and scores 1 VP. While you have fewer than 4 turtles, a hatchling also becomes a new turtle on its nest. With no turtles at all, one returns to any shore reef."), ("Day", "3 actions: move, battle, feed, lay, or build a nest in an empty slot of a shore reef where you have a turtle."), ("Dusk", "Nothing.")],
        scores=["1 VP per egg laid, and 1 VP more when it hatches.", "Nests score when built: 1, 2, 3."],
        note="Female sea turtles swim hundreds of kilometers to lay their eggs on the beach where they hatched. Crabs and birds wait for the hatchlings.",
    ),
    dict(
        key="snake", name="Sea Snake", role="Serpent", glyph="snake", color="snake",
        latin="Laticauda colubrina", common="banded sea krait",
        complexity=2, reach=7,
        tagline="The more it eats, the longer it gets.",
        idea="One snake with one long body stretched across the map. It grows by eating and by shedding its skin, and it coils around whatever it catches. A single hit in the middle can cut it in two.",
        core="One long body; grow by eating, don't get cut",
        scores_by="stretching across the map",
        choice="Stretch out for points, or coil back on yourself to squeeze. Shed your tail for gear, or keep the length for actions.",
        ways=dict(Move="Slither the Head", Battle="Bite at the Head, or coil", Recruit="Grow by eating, or molt", Craft="Shed tail segments"),
        rules=[
            ("One body", "Your Head and segments form a line, each piece in the same reef as the piece ahead of it or next to it. To slither, move the Head one channel, ignoring rule; each segment moves to where the piece ahead of it was. Your pieces can't be pushed."),
            ("Grow", "For each enemy warrior your bites and coils remove, add a segment at your tail. At Dawn, a snake with fewer than 4 pieces adds a segment."),
            ("Cut", "A hit on your snake always removes the piece nearest the tail among those it can hit. If that splits the body, the part behind the cut is lost. If the Head goes, the next segment becomes the Head; if nothing is left, a new Head arrives at any gate at your next Dawn."),
        ],
        quirk=("Coil", "When your Head slithers into a reef where the rest of your body already is, every other faction's warriors there take 1 hit."),
        craft="Shed your tail: each segment you take off the end of your snake pays any suit. You can't shed the Head.",
        pieces="1 Head and 14 segments, all warriors.",
        setup="The Head and 3 segments in one reef that isn't a gate.",
        turn=[("Dawn", "Nothing."), ("Day", "2 actions, or 3 once you have 9 pieces: slither, bite (battle in the Head's reef), or molt (discard a card: add 2 segments)."), ("Dusk", "Score.")],
        scores=["At Dusk, 1 VP for every 2 reefs your snake is in, rounded up."],
        note="Banded sea kraits hunt eels in reef crevices, then come ashore to digest, shed their skin and lay eggs.",
    ),
    dict(
        key="remoras", name="Remoras", role="Hitchhiker", glyph="remora", color="remora",
        latin="Echeneis naucrates", common="live sharksucker",
        complexity=2, reach=3,
        tagline="Why swim when you can ride?",
        idea="Remoras don't hold ground. They stick to other factions, go where they go, clean them for a fee and pile into their fights.",
        core="Ride other factions, clean them, share their kills",
        scores_by="riding and scraps",
        choice="Ride the fiercest fighter for scraps, or as many factions as you can for Dusk points. Cleaning feeds you both: is that host worth feeding?",
        ways=dict(Move="Swim free, or ride along", Battle="Pile into your host's battles", Recruit="1 a Dawn, and hitch", Craft="Reefs where you ride"),
        rules=[
            ("Attach", "Stick free remoras onto another faction's warriors in the same reef. Attached remoras can't be hit. When the last of those warriors leaves the reef, the remoras go with it; if those warriors are all removed, the remoras drop off."),
            ("Tiny", "Remoras never count for rule, and free remoras move ignoring rule. They only play in games with at least two other factions."),
            ("Pile on", "When a faction you ride starts a battle, your remoras on it in that reef add 1 hit each, up to 2."),
        ],
        quirk=("Cleaning station", "Once per turn, an action in a reef where your remoras ride a faction: that faction draws a card, and so do you."),
        craft="Each reef where 3 or more of your remoras ride pays its suit.",
        pieces="8 remoras.",
        setup="3 remoras at any gate.",
        turn=[("Dawn", "1 remora arrives at any gate."), ("Day", "3 actions: swim (move free remoras one channel), attach, let go, clean, or hitch (discard a card: 2 new remoras attach to a faction's warriors in a reef of the card's suit)."), ("Dusk", "Score your rides.")],
        scores=["1 VP each time a faction you ride removes pieces of a faction other than yours, once per attack.", "At Dusk, 1 VP per faction that 2 or more of your remoras ride."],
        note="A remora's front fin has become a suction disc. It rides sharks, turtles and whales, eats their leftovers and picks off their parasites.",
    ),
    dict(
        key="crabs", name="Hermit Crabs", role="Merchant", glyph="hermit", color="hermit",
        latin="Dardanus", common="hermit crabs",
        complexity=3, reach=5,
        tagline="Every shell has a price.",
        idea="The reef's only shell shop. Other factions buy shells to protect their warriors, and every card they pay becomes an action for the crabs. And whenever someone's home is wrecked, a crab moves in.",
        core="Sell armor; spend the payments as actions",
        scores_by="markets",
        choice="Price shells high to bank fewer, richer sales, or low to sell more. Every Till card is an action or a market.",
        ways=dict(Move="Standard moves, one Till card", Battle="Standard battles, one Till card", Recruit="3 crabs at a market", Craft="Your markets"),
        rules=[
            ("Shell shop", "At the start of another faction's Day, it may buy shells from your pool at your price, paying in cards. Each shell goes in a reef where the buyer has pieces and ignores the next hit on the buyer's pieces there, then comes back to your pool. Shells are rented: all of them come back at your Dawn. Shells can't be hit or taken."),
            ("Till", "Cards paid to you go into your Till, and at Dawn you may add cards from your hand. Every Day action costs one Till card."),
            ("Own shells", "Your crabs can wear shells from your pool for free, the same way."),
        ],
        quirk=("Vacancy chain", "When another faction's building is removed from a reef with your crabs, one of your markets moves into the empty slot for free."),
        craft="Your markets pay their reef's suit.",
        pieces="12 crabs. 5 markets (buildings). 8 shells.",
        setup="1 market and 4 crabs in any reef.",
        turn=[("Dawn", "Add cards to your Till."), ("Day", "Actions, one Till card each: move, battle, recruit (3 crabs at a market, or at any gate if you have no market), or build a market in a reef you rule (the Till card must match the reef's suit)."), ("Dusk", "Set your shell price (1 to 3 cards), then score your markets.")],
        scores=["At Dusk, 1 VP per market on the map."],
        note="When a big empty shell turns up, hermit crabs line up by size and swap homes down the line. Biologists call it a vacancy chain.",
    ),
    dict(
        key="anglers", name="Anglerfish", role="Trapper", glyph="abyss", color="angler",
        latin="Ceratias holboelli", common="deep-sea anglerfish",
        complexity=3, reach=6,
        tagline="Follow the light.",
        idea="They live in the Trench and never swim the reef. They hang lures with real rewards over it, and whoever takes the bait is hooked.",
        core="Hang lures with real rewards; eat whoever bites",
        scores_by="eating at lures and hooks",
        choice="Treasure hooks a faction so you can snap it anywhere, Shelter protects whoever you want alive, Glory draws rulers in. Snap big to eat more, or small to keep anglers for tomorrow.",
        ways=dict(Move="Move your lures", Battle="Snap from the Trench", Recruit="Discard for anglers", Craft="Lures, which go dark"),
        rules=[
            ("Lures", "At Dawn, place or move your lures in rim reefs or their neighbors and pick each lure's offer. <i>Treasure</i>: a faction that moves warriors here draws a card, once per turn. <i>Shelter</i>: warriors here can't be battled except by you. <i>Glory</i>: a faction that rules this reef at the end of its turn scores 1 VP. You start with 2 lures; the 3rd and 4th unlock after your snaps remove 4 and 8 warriors."),
            ("Snap", "At Dusk, at up to 2 lure or rim reefs with enemy warriors, you may bring up to 3 anglers out of the Trench and battle one faction there. You deal 1 hit before the dice, the first bite. Survivors sink back into the Trench."),
            ("The Trench", "Your anglers live in the Trench, off the map, where nothing can attack them."),
        ],
        quirk=("Hooked", "A faction that takes a Treasure lure's card is hooked until your next Dusk: you can snap at it in any reef."),
        craft="A lure pays its reef's suit, then goes dark: no offer, and no snap there, until you relight it at Dawn.",
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
        idea="One giant octopus whose eight arms think for themselves. Each arm keeps a standing order and carries it out every turn, in number order. Whatever the arms steal goes into the garden, and when trouble comes, the octopus inks and jets away.",
        core="Program eight arms; steal treasures for the garden",
        scores_by="its garden",
        choice="Program the arms well and they run themselves. Spend treasures on gear, or keep them for the garden.",
        ways=dict(Move="Reach orders; the Mantle jets", Battle="Grab orders", Recruit="Lost arms regrow", Craft="Spend garden treasures"),
        rules=[
            ("Orders", "At Dawn, put 1 or 2 cards under arms with no order, and you may swap the orders of two arms. The suit is the order. Kelp: <i>reach</i>, move that arm one channel, ignoring rule. Sponge: <i>grab</i>, battle in its reef. Pearl: <i>steal</i>, take an enemy token from its reef, or a random card from a faction with pieces there, into your garden. Moon: pick each time."),
            ("Recoil", "Arms act from 1 to 8. If an arm can't carry out its order, discard the orders on it and on every higher arm, and lose 1 VP per order discarded."),
            ("Reach", "Arms stay within 2 channels of the Mantle, your body. After the Mantle moves, arms out of reach snap back to it. Lost arms keep their orders, skip their turn, and regrow at the Mantle at Dusk."),
        ],
        quirk=("Ink", "When battled, you may discard a card: the battle ends before any hits, and your pieces there jet to a neighboring reef."),
        craft="Garden treasures: a stolen card pays its suit, a stolen token pays any suit. Crafting spends them.",
        pieces="The Mantle and 8 numbered arms, all warriors.",
        setup="The Mantle and all 8 arms in one reef that isn't a gate.",
        turn=[("Dawn", "Add orders, and swap two if you like."), ("Day", "Arms act, 1 to 8. Then the Mantle may move to any reef with one of your arms."), ("Dusk", "Regrow arms, then score the garden.")],
        scores=["Each treasure scores 1 VP when stolen, token or card.", "At Dusk, 1 VP per kind of treasure in your garden: each token type, and each suit of stolen card. If the Mantle is removed, the garden is lost and the Mantle returns at Dawn beside any arm, or at any gate if no arm is left."],
        note="About two thirds of an octopus's neurons are in its arms. Each arm can taste and grab on its own.",
    ),
    dict(
        key="cuttlefish", name="Cuttlefish", role="Illusionist", glyph="court", color="court",
        latin="Sepia apama", common="giant cuttlefish",
        complexity=4, reach=5,
        tagline="Trust your eyes. Everyone else does.",
        idea="Cuttlefish change color in a heartbeat. They repaint reefs to a new suit for every faction, hypnotize whatever stands in the way, and score by ruling patterns across the map.",
        core="Repaint the suits of reefs; rule patterns",
        scores_by="patterns on the map",
        choice="Paint toward your own patterns, hypnotize rivals out of the reefs you need, or hatch more cuttlefish to hold them.",
        ways=dict(Move="Standard moves", Battle="Standard battles", Recruit="Hatch, and pigments", Craft="Your pigments"),
        rules=[
            ("Paint", "Discard a card to put a pigment of its suit (Moon: any suit) in a reef with your cuttlefish, or to move one of your pigments there. That reef is that suit for every faction and every rule."),
            ("Camouflage", "To battle your cuttlefish in a painted reef, a faction must first discard a card of that reef's suit."),
            ("Galleries", "Three face-up Gallery cards each show a pattern, such as three connected reefs of one suit. At Dusk, score every pattern made of reefs you rule, then replace each one you scored."),
        ],
        quirk=("Hypnotize", "Discard a card: move up to 3 warriors of another faction from a reef with your cuttlefish to a neighboring reef. Cuttlefish hunt by rippling stripes until the prey freezes."),
        craft="Your pigments pay their suit.",
        pieces="12 cuttlefish. 6 pigments (tokens, 2 per suit).",
        setup="3 cuttlefish in each of two reefs.",
        turn=[("Dawn", "1 cuttlefish arrives in each reef with your pigment, or 2 at any gate if you have no pigment on the map."), ("Day", "3 actions: move, battle, paint, hypnotize, or hatch (discard a card: 2 cuttlefish in a reef of its suit where you have cuttlefish, or in any reef of its suit if you have none on the map)."), ("Dusk", "Score galleries.")],
        scores=["At Dusk, every Gallery pattern you rule scores its 2 or 3 VP."],
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
    ("Starfish", "Coral", "devouring eats coral, and Rubble takes its slots"),
    ("Starfish", "Hermit Crabs", "devouring eats markets"),
    ("Starfish", "Sea Turtles", "devouring eats nests"),
    ("Sardines", "Sea Turtles", "a wall of fish shoves turtles off the nest"),
    ("Sardines", "Lionfish", "a wall of fish breaks up reefs they hold alone"),
    ("Cuttlefish", "Lionfish", "hypnosis breaks up reefs they hold alone"),
    ("Octopus", "Sharks", "ink makes hunts miss"),
    ("Sea Snake", "Octopus", "coils crush lone arms"),
    ("Coral", "Starfish", "stinging polyps fight the outbreak"),
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
    ("Crafting", "Only buildings crafted, so 11 of the 14 factions could never craft.", "Every faction crafts. Each plate names its crafting pieces: Blood, big schools, Rubble, swarms, lures, pigments, garden treasures and more."),
    ("Doing things", "Most factions were missing one of moving, battling, recruiting or crafting, and the plates said “never”.", "Every faction moves, battles, recruits and crafts, each in its own way. The table below shows how."),
    ("Choices", "Most factions had one obviously best move every turn.", "Each plate names the choice its player faces, and its rules pull against each other: the same Blood, Sand, Food or card can go two or three ways."),
    ("Quirks", "The plates were dry.", "Every faction has one quirk taken from the real animal: slime pajamas, immortal jellyfish, stuffed lionfish, hatchlings running a gauntlet."),
    ("Pushing", "There was no shared word for moving someone else's warriors.", "A push moves another faction's warriors for it, ignoring rule. Stings and riders treat it as that faction's own move."),
    ("Recruiting", "Several factions could only add warriors in special cases, so a bad turn could leave them stuck.", "Every faction can add warriors every turn, most of them by discarding any card."),
]

CHANGES_FACTIONS = [
    ("Sharks", "Hunt, feed, repeat. Blood only ever meant points.", "Each Blood is a meal, a frenzy of 2 new sharks, or gear. Sharks at a gate can dive to any other gate."),
    ("Sardines", "They could only move and leave.", "Big schools mob, rallies add fish with any card, big schools craft, and a school of 6 shoves weaker factions aside."),
    ("Lionfish", "Move and gorge, nothing else.", "Breeding picks where the young go, a release brings more with any card, gorging can eat tokens and buildings, and stuffed lionfish can't move."),
    ("Starfish", "Stripping was automatic, so starfish only chose where to crawl.", "Devour is an action: eat a building or lay Rubble. Starfish crawl toward buildings, battle, and craft with Rubble."),
    ("Coral", "Grow or spawn, and the better one was usually obvious.", "Coral battles, bleaches coral for cards, and scores a Nursery point for every coral reef with visitors."),
    ("Jellyfish", "They could never battle or craft, and a wiped-out swarm was gone.", "A pulse battles once a turn, swarms craft, and the last jellyfish removed leaves a cyst that becomes 2."),
    ("Parrotfish", "Sand only built sandbars and islands.", "Sand also pays for gear, and a slime cocoon protects a reef at the cost of pinning it."),
    ("Sea Turtles", "They could never battle, and the only choice was which way to swim.", "Turtles battle, ride currents, spend Food on gear, and their hatchlings run a gauntlet."),
    ("Sea Snake", "It grew only by biting.", "Molting grows it with any card, the tail pays for gear, and coiling back on itself squeezes everything there."),
    ("Remoras", "Attach and wait.", "Remoras clean their hosts for a card and a point, pile into their fights, and hitch on with any card."),
    ("Hermit Crabs", "They couldn't battle.", "Crabs battle, and move into wrecked homes for free."),
    ("Anglerfish", "They only snapped.", "Taking the bait hooks a faction anywhere on the map, and a deep hoard of anglers scores."),
    ("Octopus", "A bad order could only recoil.", "Swap two orders each Dawn, ink away from battles, and spend treasures on gear."),
    ("Cuttlefish", "The app ticked every pattern you held, but only one scored. New cuttlefish only came at pigments, so the faction could stall.", "Every pattern you hold scores, and the easy two-reef patterns are gone. A hatch brings 2 cuttlefish with any card, and hypnosis pushes rivals away."),
]

# Draft 4: every faction checked again for ways to be wiped out or stuck, now that each must be able to
# add warriors every turn. (faction, what could go wrong, what stops it now)
SOFTLOCKS = [
    ("Sharks", "New sharks depended on Blood already on the map, so a quiet table meant 1 shark a turn.", "1 shark every Dawn, 2 with none on the map, and any Blood can become 2 sharks in a frenzy."),
    ("Sardines", "More than 3 fish a turn needed cards of the gate's suit.", "3 arrive every Dawn, and a rally adds 2 with any card, in a reef of its suit."),
    ("Lionfish", "New lionfish only came from breeding, or after being nearly wiped out.", "A release brings 2 at any gate with any card, every turn."),
    ("Starfish", "None found: spawning works in any reef of the card's suit when none are on the map.", "No change."),
    ("Coral", "Without cards, coral could neither grow nor spawn.", "Bleaching turns a coral into 2 cards in any turn you don't grow."),
    ("Jellyfish", "A reef wiped clean stayed empty until a bloom of its suit.", "The last jellyfish removed leaves a cyst that becomes 2 at Dawn."),
    ("Parrotfish", "A cocoon could have pinned the only parrotfish on the map.", "Cocoons last only until your next Dusk, and 2 parrotfish still arrive every Dawn."),
    ("Sea Turtles", "Eaten hatchlings could have left the turtles with no way back.", "An eaten hatchling still leaves its nest, and with no turtles at all, one returns to a shore reef."),
    ("Sea Snake", "Growing needed prey at the Head.", "A molt adds 2 segments with any card."),
    ("Remoras", "Only 1 remora arrived a turn.", "A hitch attaches 2 more with any card, in a reef of its suit."),
    ("Hermit Crabs", "None found: recruits arrive at any gate with no market, and any card can go into the Till.", "No change."),
    ("Anglerfish", "None found: any card adds anglers to the Trench.", "No change."),
    ("Octopus", "A badly ordered stack of arms recoiled every turn.", "Swap two orders at every Dawn."),
    ("Cuttlefish", "With a pigment on the map, new cuttlefish only arrived at pigments. Lose the cuttlefish around them and the faction stalled.", "A hatch brings 2 cuttlefish with any card, every turn."),
]

# What 126 bot games showed after the draft 4 redesign, and what changed. (faction, found, change)
BALANCE = [
    ("Every faction", "Crafting for everyone added a new stream of points: first games ended in round 5 or 6.", "Each faction's other scoring was trimmed below, so games end around round 9 again."),
    ("Coral", "Bleached a coral for 2 cards and regrew it for points in the same turn: 4.8 VP a round.", "A turn that bleaches can't grow, bleached coral is gone for good, coral scores 1 VP per 4 on the map, and each reef of coral crafts once."),
    ("Cuttlefish", "Scoring every pattern held was 6 VP a round.", "Patterns count reefs you rule, not just reefs you're in, and are worth 2 or 3 VP."),
    ("Lionfish", "Bred into every neighbor and held reefs alone: won 20 of 29.", "Breed in reefs with 3 or more, score reefs held alone with 6 or more, gorge only warriors, and release once a turn."),
    ("Remoras", "Cleaned every host every turn.", "One cleaning a turn, for a card each. Riding scores only hosts with 2 or more remoras, and 8 remoras instead of 10."),
    ("Anglerfish", "Every lure also paid for crafting every turn, and a deep hoard scored most Dusks.", "A lure that pays goes dark until you relight it at Dawn. Only Treasure hooks. No hoard."),
    ("Starfish", "Laying Rubble scored twice: when laid, and every Dusk after.", "Rubble scores only at Dusk."),
    ("Octopus", "The bot never set orders, because an order looked no better than the card it cost.", "Bots value a standing order that will act; swapping orders has to help."),
    ("Parrotfish", "Spent all their Sand on gear and never raised islands.", "Gear costs 3 Sand a suit, and islands cost 4 Sand and score 3 VP when raised."),
    ("Sea Turtles · Hermit Crabs", "A little behind the rest.", "Turtles lay 2 eggs plus 1 per Food; crabs recruit 3 and have 5 markets."),
]

SHARED_RULES = [
    ("Twelve reefs", "Each reef has a suit (Kelp, Sponge or Pearl) and one to three slots for buildings. Channels connect neighboring reefs.", False),
    ("Edges of the map", "The four corner reefs are gates to the open ocean. The top row is the shore. The three reefs touching the Trench are the rim.", True),
    ("A turn", "Dawn, Day, Dusk. At Dusk every faction draws a card and discards down to five. A round is one turn for every faction.", False),
    ("Pieces", "<b>Warriors</b> fight and count for rule. <b>Buildings</b> fill slots and count for rule. <b>Tokens</b> sit in a reef and don't count. <b>Channel markers</b> sit on channels, one per channel, and can't be hit. Removed pieces go back to their owner's supply.", False),
    ("Rule and moving", "You rule a reef when you have the most warriors and buildings there; a tie means nobody does. A move takes any number of your warriors to a neighboring reef, and you must rule where you leave or where you arrive. Placing pieces from your supply isn't moving. A <b>push</b> moves another faction's warriors for it, ignoring rule; it counts as that faction's move.", False),
    ("Currents", "Moving along a current never needs rule. Four currents circle the middle of the map as the Gyre.", True),
    ("Attacks and hits", "An attack is a battle or any ability that deals hits. Each hit removes one of the target's pieces in that reef, warriors first, and its owner picks which. Removing an enemy building or token scores 1 VP. Removing warriors scores nothing unless a plate says so.", False),
    ("Battle", "Pick a reef with your warriors and a faction with pieces there. Roll two dice showing 0 to 3: you deal the higher, the defender deals the lower, each capped by its warriors there. A defender with no warriors takes 1 extra hit. An Ambush card of the reef's suit deals 2 hits before the roll.", False),
    ("Cards", "54 cards in Kelp, Sponge, Pearl and wild Moon. Once per turn, a faction with a fixed number of Day actions may discard a card for one more.", False),
    ("Gear", "Every faction crafts. Its plate names its crafting pieces, and each piece pays one suit, once per turn. Pay a gear card's cost to craft it: score its VP and keep its lasting bonus.", False),
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
        if len(f.get("quirk", ())) != 2:
            errors.append(f"{n}: needs exactly one quirk")
        if not f.get("craft"):
            errors.append(f"{n}: every faction crafts, so its plate must name its crafting pieces")
        if sorted(f.get("ways", {})) != sorted(VERBS) or not all(f["ways"].values()):
            errors.append(f"{n}: must say how it does each of {', '.join(VERBS)}")
        if not f.get("choice"):
            errors.append(f"{n}: must name the choice its player faces")
        if len(f["scores"]) > 2:
            errors.append(f"{n}: scores in more than 2 ways")
        if [p for p, _ in f["turn"]] != ["Dawn", "Day", "Dusk"]:
            errors.append(f"{n}: turn must be Dawn, Day, Dusk")
        text = " ".join([t for _, t in f["rules"]] + [t for _, t in f["turn"]] + f["scores"] + [f["pieces"], f["setup"], f["quirk"][1], f["craft"], f["choice"]] + list(f["ways"].values()))
        for other in names:
            if other != n and re.search(rf"\b{re.escape(other)}\b", text):
                errors.append(f"{n}: a rule names another faction ({other})")
        if "draw a card" in " ".join(t for p, t in f["turn"] if p == "Dusk").lower():
            errors.append(f"{n}: repeats the shared Dusk draw")
    for a, b, _ in PRESSURE:
        for x in (a, b):
            if x not in names:
                errors.append(f"pressure edge names unknown faction {x}")
    reviewed = [n for n, _, _ in SOFTLOCKS]
    for n in names:
        if n not in reviewed:
            errors.append(f"{n}: missing from the softlock review")
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


ART_SVG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "art", "svg")
PORTRAITS = {
    "sharks": "shark", "sardines": "sardines", "lionfish": "lionfish", "starfish": "starfish", "coral": "coral",
    "jellyfish": "jellyfish", "parrotfish": "parrotfish", "turtles": "turtle", "snake": "snake", "remoras": "remora",
    "crabs": "crab", "anglers": "angler", "octopus": "octopus", "cuttlefish": "cuttlefish",
}


def portrait(f, cls=""):
    """The faction's portrait, the same picture the app shows (made by art/build_art.py)."""
    with open(os.path.join(ART_SVG, PORTRAITS[f["key"]] + ".svg")) as fh:
        svg = fh.read().strip()
    return svg.replace("<svg ", f'<svg class="{cls}" aria-hidden="true" ', 1)


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


def ways_list(f):
    items = "".join(f"<div><dt>{v}</dt><dd>{esc(f['ways'][v])}</dd></div>" for v in VERBS)
    return f'<dl class="ways">{items}</dl>'


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
    qt, qd = f["quirk"]
    turn = "".join(f"<dt>{p}</dt><dd>{esc(d)}</dd>" for p, d in f["turn"])
    scores = "".join(f"<li>{esc(s)}</li>" for s in f["scores"])
    return f'''
  <article class="plate" id="{f["key"]}" style="--fc:var(--f-{f["color"] if f["color"] != "court" else "court-a"})">
    <div class="plate-head">
      <div class="specimen">{portrait(f)}</div>
      <div class="plate-title"><p class="cap">Plate {roman(i)} · {f["role"]}</p><h3>{f["name"]}</h3><p class="taxon"><i>{f["latin"]}</i> · {f["common"]}</p></div>
      <dl class="stats"><dt>Complexity</dt><dd>{pips(f["complexity"])}</dd><dt>Reach</dt><dd>{f["reach"]}</dd></dl>
    </div>
    <p class="tagline">“{f["tagline"]}”</p>
    <p class="idea">{esc(f["idea"])}</p>
    <p class="choice"><b>The choice</b>{esc(f["choice"])}</p>
    {ways_list(f)}
    <div class="plate-body">
      <div>
        <h4>Its three rules</h4>
        <ul class="mech">{rules}</ul>
        <h4 class="qh">Quirk</h4>
        <p class="quirk"><b>{qt}.</b> {esc(qd)}</p>
      </div>
      <div class="side">
        <div><h4>Crafts with</h4><p>{esc(f["craft"])}</p></div>
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
    head = "".join(f"<th scope=\"col\" class=\"w\">{v}</th>" for v in VERBS)
    rows = []
    for i, f in enumerate(FACTIONS, 1):
        cells = "".join(f'<td class="w">{esc(f["ways"][v])}</td>' for v in VERBS)
        rows.append(
            f'<tr><td class="num">{roman(i)}</td><th scope="row"><a href="#{f["key"]}">{portrait(f, "mini")}<span>{f["name"]}</span></a></th>'
            f'{cells}<td class="by">{esc(f["scores_by"])}</td></tr>'
        )
    return f'''<div class="table-scroll"><table class="glance">
      <thead><tr><th scope="col" class="num">Plate</th><th scope="col">Faction</th>{head}<th scope="col">Scores by</th></tr></thead>
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
    items = [
        ("One idea.", "Every faction is built around a single core idea, with exactly three rules and one quirk."),
        ("Its own way.", f"All {len(FACTIONS)} factions move, battle, recruit and craft, each in its own way. The table below shows how."),
        ("A real choice.", "Every plate names the choice its player faces each turn. The same Blood, Sand, Food or card can go two or three ways, and no option is right every time."),
        ("A quirk from the animal.", "Each faction has one rule taken straight from the real animal, and it should make you smile."),
        ("Shared words.", "Every rule is written with the same words: warrior, building, token, channel marker, attack, hit, push, rule. A sting is a hit, so a turtle's shell ignores it without a special case."),
        ("No names.", "No rule mentions another faction. Every interaction comes from the shared words."),
        ("Both sides agree.", "Every “hurts” on one plate appears as “hurt by” on the other."),
        ("No dead ends.", "Every faction can add warriors every turn, however badly things go. The softlock review above checks each one, and bot games test it."),
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


def softlocks_html():
    rows = "".join(
        f'<tr><th scope="row">{n}</th><td>{esc(risk)}</td><td>{esc(fix)}</td></tr>' for n, risk, fix in SOFTLOCKS
    )
    return f'''<div class="table-scroll"><table class="glance soft">
      <thead><tr><th scope="col">Faction</th><th scope="col">What could go wrong</th><th scope="col">What stops it now</th></tr></thead>
      <tbody>{rows}</tbody></table></div>'''


def balance_html():
    rows = "".join(f'<tr><th scope="row">{n}</th><td>{esc(found)}</td><td>{esc(change)}</td></tr>' for n, found, change in BALANCE)
    return f'''<div class="table-scroll"><table class="glance soft">
      <thead><tr><th scope="col">Faction</th><th scope="col">What bot games showed</th><th scope="col">What changed</th></tr></thead>
      <tbody>{rows}</tbody></table></div>
      <p class="note">After these changes every faction scores 2.2 to 2.9 VP a round against the bots, and a game usually ends around round 9 (5 to 22 in the bot games).</p>'''


def changes_html(items):
    return "".join(f'<li><b>{t}</b><p><span class="was">Draft 3:</span> {esc(w)}</p><p><span class="now">Now:</span> {esc(n)}</p></li>' for t, w, n in items)


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
        "%%SOFTLOCKS%%": softlocks_html(),
        "%%BALANCE%%": balance_html(),
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
             scores=f["scores"], ways=f["ways"], choice=f["choice"],
             quirk=dict(title=f["quirk"][0], text=strip(f["quirk"][1])), craft=f["craft"],
             complexity=f["complexity"], reach=f["reach"])
        for f in FACTIONS
    ]
    Path(path).write_text(json.dumps(dict(plates=plates, shared=[dict(title=t, text=re.sub(r"</?b>", "", d)) for t, d, _ in SHARED_RULES]), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


TEMPLATE = Path(__file__).with_name("field_guide_template.html").read_text(encoding="utf-8")

if __name__ == "__main__":
    here = Path(__file__).parent
    build(sys.argv[1] if len(sys.argv) > 1 else here / "field-guide.html")
    export_plates(here.parent / "engine" / "src" / "main" / "resources" / "plates.json")
