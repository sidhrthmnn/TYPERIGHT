"""Authored, fictional English/Manglish editing data; no user messages or scraped prose.

Copyright TypeRight contributors. Data: CC BY-SA 4.0; generator: Apache-2.0. Synthetic spelling perturbations
are generated from authored targets, not from the typo holdout or personal data.
Template and target-word families are split before any examples are generated.
"""
import argparse
import hashlib
import json
import pathlib
import random
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = "Qwen/Qwen3-0.6B"
REVISION = "c1899de289a04d12100db370d81485cdf75e47ca"
TASKS = {
    "PROOFREAD": "Correct spelling, grammar and punctuation with minimal changes.",
    "AUTO_FORMAT": "Correct errors and format readable paragraphs or lists.",
    "POLISH": "Improve wording, clarity and flow while correcting errors.",
    "PROFESSIONAL": "Rewrite in a respectful professional tone.",
    "CASUAL": "Rewrite in a natural friendly tone.",
    "SHORTEN": "Make concise while keeping all essential information.",
    "EXPAND": "Use complete clear sentences without inventing facts.",
    "REPHRASE": "Use alternate wording with the same meaning.",
    "VOICE_CLEANUP": "Clean dictation and self-corrections; remove only actual disfluencies.",
    "RAMBLE": "Clean dictation and self-corrections; remove only actual disfluencies.",
}
GUARD = ("Preserve meaning, negation, names, numbers, URLs, emojis, language and code switching. "
         "Keep Manglish in Latin letters. Edit questions without answering them. Return only the replacement text.")
TARGETS = {
    "train": "message meeting project tomorrow because different receive separate really please report schedule document presentation available necessary appointment address answer beautiful colleague decide describe develop environment everyone example experience favorite February friend important interest knowledge language maintenance minute moment occasion official organize practice probably promise remember restaurant return serious special successful surprise together unfortunately useful Wednesday writing".split(),
    "calibration": "account agreement beginning calendar communication complete consider delivery description discussion early enough explain familiar government information material opportunity prepare recommend regular request responsibility situation understand".split(),
    "holdout": "finally library business definitely believe accommodation convenient immediately independent achieve committee embarrass exercise foreign grammar guarantee hospital invitation permission pronunciation questionnaire relevant sentence stationery threshold vehicle visible whether".split(),
}
NAMES = {"train": ["Anu", "Arjun", "Maya", "Leena"], "calibration": ["Ravi", "Fathima"], "holdout": ["Zorabel", "Nandhu"]}
MANGLISH = {
    "train": ["Njan nale officeil varam.", "Enikku ee meetinginu samayam illa.", "Nee innu veettil undo?", "Sheri, njan pinne vilikkam.", "Naale projectinte details ayakkam.", "Ithu nalla idea aanu.", "Njan ippol busy aanu.", "Nammal naale kaanam.", "Enikku athu venda.", "Avan innu varilla.", "Nee eppol varum?", "Ivide ellam shari aanu."],
    "calibration": ["Ningal innale evide aayirunnu?", "Athinte answer pinne parayam.", "Ente koode varunnundo?"],
    "holdout": ["Njan tomorrow officeil varilla.", "Nale meetinginu njanum varum.", "Shari, aa projectinte update ayakku.", "Aval ivide vannittilla.", "Njan ippo purathaanu."],
}

# Non-nouns need grammatical contexts. Style requests use only noun targets.
ADVERBS = set("tomorrow really probably together unfortunately finally definitely immediately early enough whether".split())
ADJECTIVES = set("different beautiful available necessary important official serious special successful useful complete familiar independent convenient foreign relevant visible regular".split())
VERBS = set("please decide describe develop organize promise remember return consider explain prepare recommend understand receive achieve believe embarrass guarantee".split())
OTHER_CONTEXTS = {
    "because": "I stayed home because it was raining.",
    "everyone": "Everyone arrived on time.",
    "separate": "Please keep the two folders separate.",
}


def context_sentence(word, split, index=0, name="Anu", number="5"):
    if word in OTHER_CONTEXTS:
        return OTHER_CONTEXTS[word]
    if word in ADVERBS:
        if word == "whether": return "I asked whether you could come."
        if word == "enough": return "We have enough time."
        if word == "unfortunately": return "Unfortunately, the trip was cancelled."
        if word in ("tomorrow", "early", "together"): return f"We can leave {word}."
        if word in ("finally", "immediately"): return f"She {word} sent the note."
        return f"I {word} need your help."
    if word in ADJECTIVES:
        return f"The result looks {word}."
    if word in VERBS:
        if word == "please": return "Could you please help me?"
        objects = {"decide": "what to do", "describe": "the idea", "develop": "the app", "organize": "the files",
                   "promise": "to return", "remember": "the details", "return": "the book", "consider": "the idea",
                   "explain": "the details", "prepare": "the report", "recommend": "a book", "understand": "the question",
                   "receive": "the letter", "achieve": "the goal", "believe": "the story", "embarrass": "anyone",
                   "guarantee": "the result"}
        return f"I can {word} {objects[word]}."
    templates = {
        "train": ["Please check the {word}.", "I need the {word} by {number}.", "Can you send the {word}, {name}?", "The {word} is ready.", "I do not need the {word}.", "We discussed the {word} yesterday."],
        "calibration": ["Could you review the {word} before {number}?", "They mentioned the {word} to {name}."],
        "holdout": ["We should talk about {word} with {name}.", "Have you checked {word} at {number}?", "That note mentions {word}."],
    }
    return templates[split][index].format(word=word, name=name, number=number)


def prompt(mode, original, literals=(), guidance=""):
    system = f"You are TypeRight's text editor. Mode: {mode}. {TASKS[mode]} {GUARD}"
    literal_line = "Keep literal spellings: " + ", ".join(json.dumps(x, ensure_ascii=False) for x in literals) + "\n" if literals else ""
    return system, (guidance + "\n" if guidance else "") + literal_line + "Original text:\n" + original


def typo(word, rng):
    if len(word) < 4:
        return word
    index = rng.randrange(1, len(word) - 1)
    operation = rng.randrange(4)
    if operation == 0:
        return word[:index] + word[index + 1:]
    if operation == 1:
        return word[:index] + word[index + 1] + word[index] + word[index + 2:]
    if operation == 2:
        return word[:index] + word[index] + word[index:]
    return word[:index] + rng.choice("abcdefghijklmnopqrstuvwxyz") + word[index + 1:]


def examples(split):
    rng = random.Random(193 + ["train", "calibration", "holdout"].index(split))
    seen = set()
    result = []
    def add(mode, original, output, family, literals=(), language="English"):
        key = (mode, original, output)
        if key in seen or original == "": return
        seen.add(key)
        system, user = prompt(mode, original, literals)
        result.append(dict(mode=mode, input=original, expected=output, system=system, user=user,
                           family=family, protected=list(literals), language=language))
    templates = {
        "train": ["Please check the {word}.", "I need the {word} by {number}.", "Can you send the {word}, {name}?", "The {word} is ready.", "I do not need the {word}.", "We discussed the {word} yesterday."],
        "calibration": ["Could you review the {word} before {number}?", "They mentioned the {word} to {name}."],
        "holdout": ["We should talk about {word} with {name}.", "Have you checked {word} at {number}?", "That note mentions {word}."],
    }
    for word in TARGETS[split]:
        for template_index, template in enumerate(templates[split]):
            for name in NAMES[split]:
                for number in ("5", "14", "203"):
                    clean = context_sentence(word, split, template_index, name, number)
                    family = f"{split}:english:{word}:template-{template_index}"
                    add("PROOFREAD", clean, clean, family, (name,) if name in clean else ())
                    for _ in range(2):
                        wrong = typo(word, rng)
                        if wrong != word:
                            # Match case at sentence start as well as lowercase mid-sentence.
                            broken = re.sub(r"\b" + re.escape(word) + r"\b", lambda m: wrong.capitalize() if m[0][0].isupper() else wrong, clean, flags=re.I)
                            add("PROOFREAD", broken, clean, family, (name,) if name in clean else ())
    # Sentence agreement and punctuation: held-out subjects and grammatical templates.
    grammar = {
        "train": [("She have the message.", "She has the message."), ("We was at the meeting.", "We were at the meeting."), ("He don't need it.", "He doesn't need it."), ("I has a project.", "I have a project."), ("They is ready.", "They are ready."), ("Are you comming tomorrow?", "Are you coming tomorrow?")],
        "calibration": [("The report are ready.", "The report is ready."), ("My colleague have arrived.", "My colleague has arrived.")],
        "holdout": [("Those documents is complete.", "Those documents are complete."), ("This library have books.", "This library has books."), ("There is two invitations.", "There are two invitations.")],
    }
    for index, (wrong, clean) in enumerate(grammar[split]):
        add("PROOFREAD", wrong, clean, f"{split}:grammar:{index}")
    for index, clean in enumerate(MANGLISH[split]):
        literals = tuple(re.findall(r"[A-Za-z]+", clean))
        family = f"{split}:manglish:template-{index}"
        for mode in TASKS:
            add(mode, clean, clean, family, literals, "Manglish")
        # Preserve every Manglish word while correcting an inserted English span.
        for word in TARGETS[split][:18]:
            mixed = clean + " " + context_sentence(word, split)
            add("PROOFREAD", mixed, mixed, family + ":" + word, literals, "English/Manglish")
            wrong = typo(word, rng)
            if wrong != word:
                add("PROOFREAD", mixed.replace(word, wrong), mixed, family + ":" + word, literals, "English/Manglish")
    # Licensed romanization families are partitioned BEFORE corruption or selection.
    # Every attested spelling stays valid; only unambiguous non-variant perturbations
    # are labelled as typos. Never infer a canonical spelling from phonetics alone.
    families = {}
    for source in sorted((ROOT / "tools/data/dakshina").glob("*.tsv")):
        for line in source.read_text(encoding="utf-8").splitlines():
            native, latin, count = line.split("\t")
            if re.fullmatch("[a-z]{4,14}", latin):
                family = families.setdefault(native, {})
                family[latin] = family.get(latin, 0) + int(count)
    split_for = lambda native: ("train" if int(hashlib.sha256(native.encode()).hexdigest()[:8], 16) % 10 < 8
                                else "calibration" if int(hashlib.sha256(native.encode()).hexdigest()[:8], 16) % 10 == 8 else "holdout")
    eligible = [(native, forms) for native, forms in families.items() if split_for(native) == split]
    eligible.sort(key=lambda p: (-sum(p[1].values()), p[0]))
    attested = {word for forms in families.values() for word in forms}
    for native, forms in eligible[:80 if split == "train" else 20]:
        word = sorted(forms, key=lambda w: (-forms[w], w))[0]
        clean = f"Manglish: {word}."
        family = split + ":dakshina:" + native
        for mode in TASKS:
            add(mode, clean, clean, family, (word,), "Manglish")
        for _ in range(5):
            broken = typo(word, rng)
            if broken != word and broken not in attested:
                add("PROOFREAD", f"Manglish: {broken}.", clean, family, (), "Manglish")
    # Authored transformations: no facts, times, recipients or commitments added.
    for name in NAMES[split]:
        for word in [w for w in TARGETS[split] if w not in ADVERBS | ADJECTIVES | VERBS | OTHER_CONTEXTS.keys()][:25]:
            for number in ("5", "14"):
                if split == "train":
                    original = f"hi {name} can you send the {word} by {number} thanks"
                    clean = f"Hi {name}, can you send the {word} by {number}? Thanks."
                    professional = f"Hello {name},\n\nCould you please send the {word} by {number}?\n\nThank you."
                    casual = f"Hey {name}, can you send the {word} by {number}? Thanks!"
                    short = f"{name}, please send the {word} by {number}."
                    rephrase = f"Hi {name}, could you send the {word} by {number}? Thank you."
                elif split == "calibration":
                    original = f"hello {name} could you review the {word} at {number} thank you"
                    clean = f"Hello {name}, could you review the {word} at {number}? Thank you."
                    professional = f"Hello {name},\n\nCould you please review the {word} at {number}?\n\nThank you."
                    casual = f"Hi {name}, could you review the {word} at {number}? Thanks!"
                    short = f"{name}, please review the {word} at {number}."
                    rephrase = f"Hello {name}, would you review the {word} at {number}? Thank you."
                else:
                    original = f"hey {name} would you check the {word} before {number} thanks"
                    clean = f"Hey {name}, would you check the {word} before {number}? Thanks."
                    professional = f"Hello {name},\n\nWould you please check the {word} before {number}?\n\nThank you."
                    casual = f"Hey {name}, would you check the {word} before {number}? Thanks!"
                    short = f"{name}, please check the {word} before {number}."
                    rephrase = f"Hey {name}, could you check the {word} before {number}? Thank you."
                outputs = dict(PROOFREAD=clean, AUTO_FORMAT=professional, POLISH=rephrase,
                               PROFESSIONAL=professional, CASUAL=casual, SHORTEN=short,
                               EXPAND=clean, REPHRASE=rephrase, VOICE_CLEANUP=clean, RAMBLE=clean)
                for mode, expected in outputs.items():
                    add(mode, original, expected, f"{split}:tone:{word}", (name, number))
                for mode in ("VOICE_CLEANUP", "RAMBLE"):
                    add(mode, "um " + original, clean, f"{split}:voice:{word}", (name, number))
    protected = ["Contact {name} at 123456 or https://example.com/" + split + ".", "@{name} uses project_id_193."]
    for name in NAMES[split]:
        for template in protected:
            clean = template.format(name=name)
            add("PROOFREAD", clean, clean, f"{split}:identifier:{name}", tuple(re.findall(r"https?://\S+|@[\w]+|[\w]+_[\w]+|\b\d+\b", clean)))
    rng.shuffle(result)
    return result


def generate(destination):
    destination.mkdir(parents=True, exist_ok=True)
    manifest = dict(version=193, base=BASE, base_revision=REVISION, seed=193,
                    data_license="CC BY-SA 4.0", sources=[
                        dict(name="TypeRight-authored fictional conversations and synthetic perturbations", license="CC BY-SA 4.0"),
                        dict(name="Dakshina v1.0 Malayalam romanization pairs", authors="Roark et al., Google Research",
                             url="https://github.com/google-research-datasets/dakshina", license="CC BY-SA 4.0",
                             files=[dict(path=str(p.relative_to(ROOT)).replace("\\", "/"), sha256=hashlib.sha256(p.read_bytes()).hexdigest())
                                    for p in sorted((ROOT / "tools/data/dakshina").glob("*.tsv"))])],
                    split_policy="disjoint target-word families, grammar templates, Manglish conversational templates and names", splits={})
    for split in TARGETS:
        rows = examples(split)
        payload = "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows)
        (destination / f"{split}.jsonl").write_text(payload, encoding="utf-8", newline="\n")
        manifest["splits"][split] = dict(count=len(rows), sha256=hashlib.sha256(payload.encode()).hexdigest())
    manifest_path = destination / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "tools/data/polish")
    print(json.dumps(generate(parser.parse_args().output), indent=2))
