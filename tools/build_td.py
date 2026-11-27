#!/usr/bin/env python3
"""Build www/td-data.json for the verse sheet. Runs in CI. Texts are fetched unmodified
from public sources and packed by surah/verse; nothing is written or changed by us.
  Muyassar : spa5k/tafsir_api (jsDelivr), edition ar-tafsir-muyassar
  Makki/Madani : Tanzil quran-data.xml (CC-BY)
  Sabab al-nuzul : mostafaahmed97/asbab-al-nuzul-dataset (MIT), from 'Sahih Asbab al-Nuzul'"""
import json, sys, time, urllib.request, xml.etree.ElementTree as ET

def get(url, tries=4):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "zad-build"}), timeout=60) as r:
                return r.read()
        except Exception as e:
            err = e; time.sleep(2 + i * 2)
    raise SystemExit("fetch failed: %s %s" % (url, err))

out = {"m": {}, "t": {}, "ab": [], "ai": {}}
total = 0
for s in range(1, 115):
    try:
        L = json.loads(get("https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir/ar-tafsir-muyassar/%d.json" % s))
    except SystemExit:
        L = json.loads(get("https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir/ar-tafsir-muyassar/%d.json" % s))
    n = max(a["ayah"] for a in L)
    arr = [""] * n
    for a in L:
        arr[a["ayah"] - 1] = a.get("text") or ""
    out["m"][str(s)] = arr
    total += sum(1 for x in arr if x)
root = ET.fromstring(get("https://tanzil.net/res/text/metadata/quran-data.xml"))
for su in root.iter("sura"):
    out["t"][su.get("index")] = "M" if su.get("type") == "Meccan" else "D"
ab = json.loads(get("https://raw.githubusercontent.com/mostafaahmed97/asbab-al-nuzul-dataset/main/data/structured/json/all.json"))
for e in ab:
    i = len(out["ab"])
    out["ab"].append({"s": e["surah"], "a": e["ayahs"], "o": e["occasions"]})
    for v in e["ayahs"]:
        out["ai"].setdefault("%d:%d" % (e["surah"], v), []).append(i)
assert len(out["m"]) == 114 and len(out["t"]) == 114, (len(out["m"]), len(out["t"]))
assert total >= 6000, total
assert len(out["ab"]) >= 100, len(out["ab"])
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
print("td-data ok: muyassar verses", total, "sabab entries", len(out["ab"]))
