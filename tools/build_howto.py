#!/usr/bin/env python3
"""Build app/src/main/assets/howto.json from free-exercise-db (Unlicense, github.com/yuhonas/free-exercise-db).

Maps every app exercise id in exercises.json that has a free-exercise-db entry to
{"src": free-db id, "similar": bool, "steps": [3-4 short steps]}. Free-db exercises map to themselves; curated ones
use MANUAL (or a free-db entry with exactly the same name). "similar" = the photos show a close relative, not the
exact machine. Steps are hand-shortened for the plan exercises (STEPS) and auto-shortened from the DB for the rest.
Photos for the plan exercises are bundled in assets/howto/<src>/{0,1}.jpg (600 px wide); others load from GitHub."""
import importlib.util, json, os, re, sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "free-exercise-db.json")
ASSETS = os.path.join(HERE, "..", "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "howto.json")

_spec = importlib.util.spec_from_file_location("build_exercise_db", os.path.join(HERE, "build_exercise_db.py"))
_db = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_db)

# app id -> (free-db id, similar)
MANUAL = {
    "incline-smith-press": ("Smith_Machine_Incline_Bench_Press", False),
    "lat-pulldown": ("Wide-Grip_Lat_Pulldown", False),
    "machine-shoulder-press": ("Machine_Shoulder_Military_Press", False),
    "chest-supported-row": ("Lying_T-Bar_Row", True),
    "pec-deck": ("Butterfly", False),
    "biceps-cable-curl-straight-bar": ("Standing_Biceps_Cable_Curl", False),
    "triceps-v-pushdown": ("Triceps_Pushdown_-_V-Bar_Attachment", False),
    "leg-press": ("Leg_Press", False),
    "lying-leg-curl": ("Lying_Leg_Curls", False),
    "leg-extension": ("Leg_Extensions", False),
    "seated-calf-raise-machine": ("Seated_Calf_Raise", False),
    "cable-crunch": ("Cable_Crunch", False),
    "machine-chest-press": ("Leverage_Chest_Press", False),
    "seated-cable-row": ("Seated_Cable_Rows", False),
    "machine-lateral-raise": ("Side_Lateral_Raise", True),
    "reverse-pec-deck": ("Reverse_Machine_Flyes", False),
    "cable-hammer-curl": ("Cable_Hammer_Curls_-_Rope_Attachment", False),
    "triceps-dip-machine": ("Dip_Machine", False),
    "dumbbell-romanian-deadlift": ("Stiff-Legged_Dumbbell_Deadlift", False),
    "hip-adduction-machine": ("Thigh_Adductor", False),
    "standing-calf-raise-machine": ("Standing_Calf_Raises", False),
    # Other curated exercises.
    "smith-bench-press": ("Smith_Machine_Bench_Press", False),
    "overhead-cable-triceps-extension": ("Cable_Rope_Overhead_Triceps_Extension", False),
    "cable-lateral-raise": ("Side_Lateral_Raise", True),
    "incline-machine-press": ("Leverage_Incline_Chest_Press", False),
    "cable-fly": ("Cable_Crossover", False),
    "preacher-curl-machine": ("Machine_Preacher_Curls", False),
    "pendulum-squat": ("Hack_Squat", True),
    "hip-thrust": ("Barbell_Hip_Thrust", False),
    "bulgarian-split-squat": ("Split_Squat_with_Dumbbells", True),
    "hip-abduction-machine": ("Thigh_Abductor", False),
    "cable-woodchopper": ("Standing_Cable_Wood_Chop", False),
    "back-extension": ("Hyperextensions_Back_Extensions", False),
    "assisted-pull-up": ("Band_Assisted_Pull-Up", True),
    "pull-up": ("Chin-Up", True),
    "dips": ("Dips_-_Chest_Version", False),
    "shrug-machine": ("Leverage_Shrug", False),
    "wrist-curl": ("Seated_Dumbbell_Palms-Up_Wrist_Curl", False),
}

# Hand-shortened steps for the plan exercises (keyed by free-db id).
STEPS = {
    "Smith_Machine_Incline_Bench_Press": [
        "Set an incline bench under the bar, upper chest in line with it.",
        "Grip wider than your shoulders and unrack with straight arms.",
        "Lower slowly to your upper chest and pause for a second.",
        "Press back up. Take twice as long going down as coming up."],
    "Wide-Grip_Lat_Pulldown": [
        "Sit with the knee pad snug on your thighs, hands wider than your shoulders.",
        "Lean back about 30° with your chest up.",
        "Pull the bar to your upper chest, driving the elbows down and back.",
        "Squeeze your back for a second, then let the bar up slowly until your arms are straight."],
    "Machine_Shoulder_Military_Press": [
        "Sit with your back on the pad and grab the handles, elbows bent at your sides.",
        "Press the handles up until your arms are straight.",
        "Hold the top for a second.",
        "Lower slowly back to the start."],
    "Lying_T-Bar_Row": [
        "Set the pad so your upper chest sits at its top edge.",
        "Lie face down, grab the handles and lift with straight arms.",
        "Row up, elbows close to your body, and squeeze your back at the top.",
        "Lower slowly until your arms are straight."],
    "Butterfly": [
        "Sit with your back flat on the pad, upper arms parallel to the floor.",
        "Bring the handles together in front of your chest.",
        "Squeeze and hold for a second in the middle.",
        "Open slowly until your chest is fully stretched."],
    "Standing_Biceps_Cable_Curl": [
        "Stand tall holding a straight bar on a low pulley, hands shoulder-width, palms up.",
        "Keep your elbows at your sides; only the forearms move.",
        "Curl until your biceps are fully contracted and hold for a second.",
        "Lower slowly to straight arms."],
    "Triceps_Pushdown_-_V-Bar_Attachment": [
        "Grab a V-bar on a high pulley, lean forward slightly.",
        "Pin your upper arms to your sides, forearms pointing up.",
        "Push down until your arms are straight and hold for a second.",
        "Let the bar up slowly; the upper arms stay still."],
    "Leg_Press": [
        "Sit with your feet shoulder-width apart in the middle of the platform.",
        "Release the safety bars and press up without locking your knees.",
        "Lower slowly until your knees reach 90°.",
        "Push back up through your heels. Lock the safety pins when you finish."],
    "Lying_Leg_Curls": [
        "Lie face down with the pad just above your heels, knees just off the bench.",
        "Hold the handles and keep your hips on the pad.",
        "Curl your heels up as far as you can and hold for a second.",
        "Lower slowly until your legs are straight."],
    "Leg_Extensions": [
        "Sit with the pad on your shins and hold the side handles.",
        "Straighten your legs fully, keeping the rest of your body still.",
        "Pause a second at the top.",
        "Lower slowly, not past 90° at the knee."],
    "Seated_Calf_Raise": [
        "Sit with the balls of your feet on the platform, heels hanging off.",
        "Set the pad snug on your lower thighs and release the safety bar.",
        "Lower your heels until your calves are fully stretched.",
        "Rise as high as you can and hold the top for a second."],
    "Cable_Crunch": [
        "Kneel under a high pulley holding the rope next to your face.",
        "Keep your hips still for the whole set.",
        "Crunch down, bringing your elbows toward your thighs, and hold briefly.",
        "Come back up slowly, keeping tension on your abs."],
    "Leverage_Chest_Press": [
        "Set the seat so the handles line up with the middle of your chest.",
        "Chest up, shoulder blades pulled back.",
        "Press the handles forward until your arms are straight.",
        "Return slowly, without letting the weight rest between reps."],
    "Seated_Cable_Rows": [
        "Sit at a low pulley with your knees slightly bent and grab the handle.",
        "Sit upright with a straight back and arms extended.",
        "Pull the handle to your belly, elbows close, and squeeze your back.",
        "Let it go forward slowly until your arms are straight."],
    "Side_Lateral_Raise": [
        "Sit or stand with the handles at your sides, elbows slightly bent.",
        "Raise your arms out to the sides up to shoulder height.",
        "Hold the top for a second.",
        "Lower slowly; keep the elbow angle fixed."],
    "Reverse_Machine_Flyes": [
        "Set the handles fully back and the seat so they're at shoulder height.",
        "Sit facing the pad and grab the handles, arms slightly bent.",
        "Sweep your arms out and back in a half circle, squeezing your rear delts.",
        "Pause, then return slowly."],
    "Cable_Hammer_Curls_-_Rope_Attachment": [
        "Hold a rope on a low pulley with palms facing each other.",
        "Stand tall with your elbows fixed at your sides.",
        "Curl up until your forearms meet your biceps and squeeze for a second.",
        "Lower slowly to straight arms."],
    "Dip_Machine": [
        "Sit firmly in the machine and grab the handles.",
        "Keep your elbows in at your sides, bent at 90°.",
        "Push down until your arms are almost straight.",
        "Let your arms come back up slowly."],
    "Stiff-Legged_Dumbbell_Deadlift": [
        "Stand with a dumbbell in each hand, feet shoulder-width, knees slightly bent.",
        "Push your hips back and lower the dumbbells along your legs with a straight back.",
        "Go down until you feel a stretch in your hamstrings.",
        "Drive your hips forward to stand back up."],
    "Thigh_Adductor": [
        "Sit in the machine with the pads on the inside of your knees and grab the handles.",
        "Squeeze your legs together slowly.",
        "Hold the contraction for a second.",
        "Let your legs open slowly; keep your upper body still."],
    "Standing_Calf_Raises": [
        "Set the pads to your height and stand under them, balls of your feet on the edge.",
        "Keep your knees straight but not locked.",
        "Rise onto your toes as high as you can and hold for a second.",
        "Lower slowly until your calves are fully stretched."],
}

_SKIP = re.compile(r"^(repeat|when you are done|when finished)|recommended amount|prescribed amount", re.I)
_CUT = re.compile(r"\s*\b(Tip|Note|Caution|Variations?)\s*:.*$", re.I)


def short_steps(instructions):
    """3-4 short steps: drop "repeat" lines and tips, keep the first sentence of each, prefer the movement steps."""
    steps = []
    for line in instructions:
        line = _CUT.sub("", line.strip())
        if not line or _SKIP.search(line):
            continue
        first = re.split(r"(?<=[.!?])\s+(?=[A-Z])", line)[0].strip()
        if len(first) > 150:
            first = first[:150].rsplit(" ", 1)[0].rstrip(",;:") + "…"
        if first:
            steps.append(first)
    if len(steps) > 4:
        steps = steps[:1] + steps[-3:]
    return steps


def main():
    free = json.load(open(SRC))
    by_id = {e["id"]: e for e in free}
    by_slug = {}
    for e in free:
        by_slug.setdefault(_db.slug(e["name"]), e["id"])
    app_ids = [e["id"] for e in json.load(open(os.path.join(ASSETS, "exercises.json")))]
    curated = {_db.slug(c[0]) for c in _db.CURATED}

    out = {}
    for aid in app_ids:
        if aid in MANUAL:
            src, similar = MANUAL[aid]
        elif aid in by_slug:
            src, similar = by_slug[aid], False
        else:
            continue
        assert src in by_id, f"{aid}: {src} is not in free-exercise-db.json"
        steps = STEPS.get(src) or short_steps(by_id[src]["instructions"])
        if not steps:
            continue
        out[aid] = {"src": src, "similar": similar, "steps": steps}

    missing = [a for a in MANUAL if a not in out]
    assert not missing, f"plan exercises without how-to: {missing}"
    for src in STEPS:
        for i in (0, 1):
            p = os.path.join(ASSETS, "howto", src, f"{i}.jpg")
            assert os.path.exists(p), f"missing bundled photo {p}"
    json.dump(out, open(OUT, "w"), separators=(",", ":"), ensure_ascii=False)
    unmapped = sorted(curated - set(out))
    print(len(out), "how-tos ->", OUT)
    print("curated without a free-db match:", ", ".join(unmapped))


if __name__ == "__main__":
    main()
