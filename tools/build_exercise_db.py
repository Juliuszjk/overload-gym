#!/usr/bin/env python3
"""Build app/src/main/assets/exercises.json from free-exercise-db (Unlicense, github.com/yuhonas/free-exercise-db)
plus curated gym exercises that the open DB lacks. Muscles are mapped to the body-map keys used by the app:
chest, delts, reardelts, traps, back, lowerback, biceps, triceps, forearms, abs, obliques, glutes, adductors,
quads, hamstrings, calves. Each exercise gets {muscle: activation 0..1}: primary 1.0, secondary 0.4."""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "free-exercise-db.json")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "exercises.json")

MAP = {
    "chest": "chest", "shoulders": "delts", "lats": "back", "middle back": "back", "traps": "traps",
    "lower back": "lowerback", "biceps": "biceps", "triceps": "triceps", "forearms": "forearms",
    "abdominals": "abs", "glutes": "glutes", "adductors": "adductors", "abductors": "glutes",
    "quadriceps": "quads", "hamstrings": "hamstrings", "calves": "calves", "neck": "traps",
}
LEGS = {"quads", "hamstrings", "glutes", "calves", "adductors"}
CORE = {"abs", "obliques", "lowerback"}
KEEP_CAT = {"strength", "powerlifting", "olympic weightlifting"}
EQUIP = {"barbell": "barbell", "dumbbell": "dumbbell", "cable": "cable", "machine": "machine",
         "body only": "bodyweight", "kettlebells": "kettlebell", "e-z curl bar": "barbell", "bands": "band"}

# name, type, equipment, muscles
CURATED = [
    ("Incline Smith Press", "push", "smith", {"chest": 1, "delts": .6, "triceps": .5}),
    ("Smith Bench Press", "push", "smith", {"chest": 1, "delts": .4, "triceps": .5}),
    ("Chest Supported Row", "pull", "machine", {"back": 1, "reardelts": .6, "biceps": .5, "traps": .4}),
    ("Pec Deck", "push", "machine", {"chest": 1, "delts": .3}),
    ("Reverse Pec Deck", "pull", "machine", {"reardelts": 1, "traps": .5, "back": .3}),
    ("Lat Pulldown", "pull", "cable", {"back": 1, "biceps": .5, "reardelts": .3, "forearms": .2}),
    ("Machine Shoulder Press", "push", "machine", {"delts": 1, "triceps": .5, "chest": .2}),
    ("Biceps Cable Curl Straight Bar", "pull", "cable", {"biceps": 1, "forearms": .4}),
    ("Triceps V Pushdown", "push", "cable", {"triceps": 1}),
    ("Overhead Cable Triceps Extension", "push", "cable", {"triceps": 1}),
    ("Cable Lateral Raise", "push", "cable", {"delts": 1, "traps": .2}),
    ("Machine Lateral Raise", "push", "machine", {"delts": 1, "traps": .2}),
    ("Seated Cable Row", "pull", "cable", {"back": 1, "reardelts": .5, "biceps": .5, "traps": .4}),
    ("Machine Chest Press", "push", "machine", {"chest": 1, "delts": .4, "triceps": .5}),
    ("Incline Machine Press", "push", "machine", {"chest": 1, "delts": .5, "triceps": .4}),
    ("Cable Fly", "push", "cable", {"chest": 1, "delts": .3}),
    ("Preacher Curl Machine", "pull", "machine", {"biceps": 1, "forearms": .3}),
    ("Bayesian Cable Curl", "pull", "cable", {"biceps": 1, "forearms": .3}),
    ("Hack Squat", "legs", "machine", {"quads": 1, "glutes": .6, "adductors": .4}),
    ("Pendulum Squat", "legs", "machine", {"quads": 1, "glutes": .5, "adductors": .4}),
    ("Leg Press", "legs", "machine", {"quads": 1, "glutes": .6, "adductors": .4, "hamstrings": .2}),
    ("Leg Extension", "legs", "machine", {"quads": 1}),
    ("Seated Leg Curl", "legs", "machine", {"hamstrings": 1, "calves": .2}),
    ("Lying Leg Curl", "legs", "machine", {"hamstrings": 1, "calves": .2}),
    ("Romanian Deadlift", "legs", "barbell", {"hamstrings": 1, "glutes": .8, "lowerback": .5, "adductors": .4}),
    ("Hip Thrust", "legs", "barbell", {"glutes": 1, "hamstrings": .4, "quads": .3}),
    ("Bulgarian Split Squat", "legs", "dumbbell", {"quads": 1, "glutes": .8, "adductors": .5}),
    ("Hip Adduction Machine", "legs", "machine", {"adductors": 1}),
    ("Hip Abduction Machine", "legs", "machine", {"glutes": 1}),
    ("Standing Calf Raise Machine", "legs", "machine", {"calves": 1}),
    ("Seated Calf Raise Machine", "legs", "machine", {"calves": 1}),
    ("Cable Crunch", "core", "cable", {"abs": 1, "obliques": .4}),
    ("Hanging Leg Raise", "core", "bodyweight", {"abs": 1, "obliques": .4, "forearms": .3}),
    ("Cable Woodchopper", "core", "cable", {"obliques": 1, "abs": .5}),
    ("Back Extension", "legs", "bodyweight", {"lowerback": 1, "glutes": .7, "hamstrings": .6}),
    ("Assisted Pull-Up", "pull", "machine", {"back": 1, "biceps": .5, "reardelts": .3}),
    ("Pull-Up", "pull", "bodyweight", {"back": 1, "biceps": .5, "reardelts": .3, "forearms": .3}),
    ("Dips", "push", "bodyweight", {"chest": .9, "triceps": 1, "delts": .4}),
    ("Face Pull", "pull", "cable", {"reardelts": 1, "traps": .6, "back": .3}),
    ("Shrug Machine", "pull", "machine", {"traps": 1, "forearms": .3}),
    ("Wrist Curl", "pull", "dumbbell", {"forearms": 1}),
    ("Cable Hammer Curl", "pull", "cable", {"biceps": 1, "forearms": .7}),
    ("Triceps Dip Machine", "push", "machine", {"triceps": 1, "chest": .4, "delts": .3}),
    ("Dumbbell Romanian Deadlift", "legs", "dumbbell", {"hamstrings": 1, "glutes": .8, "lowerback": .5, "adductors": .4, "forearms": .3}),
]


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def kind(force, muscles):
    prim = [m for m, w in muscles.items() if w >= 1]
    if any(m in LEGS for m in prim):
        return "legs"
    if prim and all(m in CORE for m in prim):
        return "core"
    return "pull" if force == "pull" else "push"


def main():
    out, seen = [], set()
    for name, t, eq, mus in CURATED:
        out.append({"id": slug(name), "name": name, "type": t, "equipment": eq, "muscles": mus})
        seen.add(slug(name))
    for e in json.load(open(SRC)):
        if e["category"] not in KEEP_CAT or not e["primaryMuscles"]:
            continue
        mus = {}
        for m in e["secondaryMuscles"]:
            if m in MAP:
                mus[MAP[m]] = 0.4
        for m in e["primaryMuscles"]:
            if m in MAP:
                mus[MAP[m]] = 1.0
        # Rear-delt work in the open DB is tagged "shoulders"; route it to the rear-delt key.
        if "delts" in mus and re.search(r"rear|reverse fly|face pull", e["name"], re.I):
            mus["reardelts"] = mus.pop("delts")
        if not mus or slug(e["name"]) in seen:
            continue
        seen.add(slug(e["name"]))
        out.append({"id": slug(e["name"]), "name": e["name"], "type": kind(e["force"], mus),
                    "equipment": EQUIP.get(e["equipment"] or "", "other"), "muscles": mus})
    json.dump(out, open(OUT, "w"), separators=(",", ":"))
    print(len(out), "exercises ->", OUT)


if __name__ == "__main__":
    main()
