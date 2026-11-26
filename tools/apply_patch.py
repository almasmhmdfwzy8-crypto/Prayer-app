#!/usr/bin/env python3
"""Applies the r102 edits to index.html (small text hunks, each located by its preceding context)."""
import json, sys, hashlib
src, patch = sys.argv[1], sys.argv[2]
t = open(src, encoding="utf-8").read()
P = json.load(open(patch, encoding="utf-8"))
if hashlib.sha256(t.encode("utf-8")).hexdigest() != P["base_sha256"]:
    raise SystemExit("index.html is not the expected base for this patch")
for h in reversed(P["hunks"]):
    key = h["ctx"] + h["old"]
    if t.count(key) != 1: raise SystemExit("hunk location not unique or missing: " + h["ctx"][-40:])
    i = t.index(key)
    t = t[:i + len(h["ctx"])] + h["new"] + t[i + len(key):]
if hashlib.sha256(t.encode("utf-8")).hexdigest() != P["result_sha256"]:
    raise SystemExit("patched index.html does not match the expected result")
open(src, "w", encoding="utf-8", newline="").write(t)
print("patched", src, len(P["hunks"]), "hunks")
