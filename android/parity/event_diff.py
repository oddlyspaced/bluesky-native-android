#!/usr/bin/env python3
"""Compare native events.jsonl with the RN metric definitions (src/analytics/metrics/types.ts).

Usage: event_diff.py <types.ts> <events.jsonl>
For each RN event name the slice uses: RN required and optional props, native count, native props seen,
props native sends that RN does not define, and RN required props that native omits.
A prop typed `| undefined` is optional in practice: JSON drops undefined, and the native log drops nulls.
"""
import json
import re
import sys
from collections import defaultdict

SLICE_EVENTS = [
    "init", "router:navigate", "account:loggedIn", "splash:signInPressed", "nav:click",
    "home:feedDisplayed", "feed:refresh", "feed:endReached", "composerPrompt:press",
    "post:view", "post:clickthroughItem", "post:clickthroughAuthor", "post:clickReply",
    "post:like", "post:unlike", "post:repost", "post:unrepost", "thread:click:showOtherReplies",
]


def parse_types(path):
    text = open(path).read().splitlines()
    specs = {}
    for i, line in enumerate(text):
        m = re.match(r"^  '?([\w:]+)'?: \{(\})?\s*$", line)
        if not m:
            continue
        name = m.group(1)
        required, optional = set(), set()
        if m.group(2):
            specs[name] = (required, optional)
            continue
        j = i + 1
        while j < len(text) and not re.match(r"^  \}", text[j]):
            km = re.match(r"^    (\w+)(\?)?:\s*(.*)$", text[j])
            if km:
                key, opt, rest = km.group(1), km.group(2), km.group(3)
                (optional if opt or "undefined" in rest else required).add(key)
            j += 1
        specs[name] = (required, optional)
    return specs


def main():
    specs = parse_types(sys.argv[1])
    seen = defaultdict(lambda: {"count": 0, "keys": set(), "missing": set()})
    for line in open(sys.argv[2]):
        line = line.strip()
        if not line:
            continue
        event = json.loads(line)
        name, props = event["name"], event.get("props", {})
        entry = seen[name]
        entry["count"] += 1
        entry["keys"] |= set(props)
        if name in specs:
            entry["missing"] |= specs[name][0] - set(props)

    print("| Event | RN props (required; optional) | Native count | Native props | Extra in native | Missing required |")
    print("|---|---|---|---|---|---|")
    problems = 0
    for name in SLICE_EVENTS + sorted(set(seen) - set(SLICE_EVENTS)):
        spec = specs.get(name)
        entry = seen.get(name, {"count": 0, "keys": set(), "missing": set()})
        if spec is None:
            rn = "not defined in RN"
            extra = ", ".join(sorted(entry["keys"])) or "-"
            problems += 1
        else:
            required, optional = spec
            rn = f"{', '.join(sorted(required)) or '-'}; {', '.join(sorted(optional)) or '-'}"
            extra = ", ".join(sorted(entry["keys"] - required - optional)) or "-"
            if extra != "-":
                problems += 1
        missing = ", ".join(sorted(entry["missing"])) or "-"
        if missing != "-":
            problems += 1
        print(f"| `{name}` | {rn} | {entry['count']} | {', '.join(sorted(entry['keys'])) or '-'} | {extra} | {missing} |")
    not_logged = [n for n in SLICE_EVENTS if seen.get(n, {}).get("count", 0) == 0]
    print()
    print(f"Mismatches (unknown name, extra prop or missing required prop): {problems}")
    print(f"Slice events not logged in this run: {', '.join(not_logged) or 'none'}")


if __name__ == "__main__":
    main()
