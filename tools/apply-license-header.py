#!/usr/bin/env python3
# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
"""
Единый заголовок лицензии для всех исходных файлов RaskolClasses.

  python tools/apply-license-header.py            # показать, что изменится (dry-run)
  python tools/apply-license-header.py --apply    # применить
  python tools/apply-license-header.py --check    # для CI: код 1, если есть файлы без заголовка v2.0

Что делает:
  * заменяет старый заголовок v1.0 на v2.0 (Java, YAML, properties, XML, SVG);
  * добавляет заголовок в файлы, где его не было (например, пакет api/ и тесты);
  * заменяет упоминания «RASKOL Proprietary License v1.0» в README, RUNBOOK, SECURITY и прочих .md
    (CHANGELOG.md не трогает: история версий не переписывается);
  * сохраняет перевод строк файла (LF или CRLF) и BOM.
"""
import re
import sys
from pathlib import Path

YEAR = "2026"
OWNER = "hayferdahmer"
LINE1 = f"© {YEAR} {OWNER}. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE."
LINE2 = "SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0"

OLD = re.compile(
    r"^(?P<prefix>[ \t]*(?://|#)?[ \t]*)© 2026 hayferdahmer — RASKOL Proprietary License v1\.0\. See LICENSE\.[ \t]*$",
    re.M,
)
NEW_MARK = "RASKOL Proprietary License v2.0"
OLD_TEXT = "RASKOL Proprietary License v1.0"

# пропускаются только служебные каталоги в КОРНЕ репозитория (пакеты вроде spec/build/ — не трогаем)
SKIP_TOP = {".git", "target", "node_modules", ".idea", ".vscode", "out", "build"}
SKIP_ANY = {".git", "node_modules"}
SLASH = {".java"}
HASH = {".yml", ".yaml", ".properties"}
XMLISH = {".xml", ".svg"}
MD_SKIP = {"CHANGELOG.md"}


def iter_files(root: Path):
    for p in sorted(root.rglob("*")):
        if not p.is_file():
            continue
        parts = p.relative_to(root).parts
        if parts[0] in SKIP_TOP or (set(parts) & SKIP_ANY):
            continue
        yield p


def process(path: Path, root: Path):
    """Возвращает (новый_текст | None, пометка)."""
    suf = path.suffix.lower()
    raw = path.read_bytes()
    bom = raw.startswith(b"\xef\xbb\xbf")
    try:
        text = raw[3:].decode("utf-8") if bom else raw.decode("utf-8")
    except UnicodeDecodeError:
        return None, "skip:not-utf8"
    nl = "\r\n" if "\r\n" in text else "\n"
    t = text.replace("\r\n", "\n")

    if suf == ".md":
        if path.name in MD_SKIP or OLD_TEXT not in t:
            return None, "ok"
        return t.replace(OLD_TEXT, NEW_MARK).replace("\n", nl), "text"

    if suf not in SLASH | HASH | XMLISH:
        return None, "skip:type"

    if NEW_MARK in t:
        if OLD_TEXT in t:                    # старое упоминание в тексте комментария
            return t.replace(OLD_TEXT, NEW_MARK).replace("\n", nl), "text"
        return None, "ok"

    m = OLD.search(t)
    if m:
        pre = m.group("prefix")
        if pre.strip():                      # // или #
            new = f"{pre}{LINE1}\n{pre}{LINE2}"
        else:                                 # внутри XML-комментария
            ind = re.match(r"[ \t]*", pre).group(0)
            new = f"{ind}{LINE1}\n{ind}{LINE2}"
        t = t[: m.start()] + new + t[m.end():]
        return t.replace(OLD_TEXT, NEW_MARK).replace("\n", nl), "replaced"

    # заголовка нет — добавляем
    if suf in SLASH:
        head = f"// {LINE1}\n// {LINE2}\n"
        t = head + t
    elif suf in HASH:
        head = f"# {LINE1}\n# {LINE2}\n"
        if t.startswith("#!"):
            first, _, rest = t.partition("\n")
            t = first + "\n" + head + rest
        else:
            t = head + t
    else:  # XML / SVG
        head = f"<!--\n{LINE1}\n{LINE2}\n-->\n"
        decl = re.match(r"\s*<\?xml[^>]*\?>[ \t]*\n?", t)
        if decl:
            t = t[: decl.end()] + head + t[decl.end():]
        else:
            t = head + t
    return t.replace(OLD_TEXT, NEW_MARK).replace("\n", nl), "added"


def main(argv):
    apply_ = "--apply" in argv
    check = "--check" in argv
    root = Path(next((a for a in argv if not a.startswith("--")), ".")).resolve()
    stats = {"replaced": 0, "added": 0, "text": 0, "ok": 0}
    missing = []
    for p in iter_files(root):
        new, tag = process(p, root)
        if tag.startswith("skip"):
            continue
        if new is None:
            stats["ok"] += 1
            continue
        stats[tag] += 1
        missing.append((p.relative_to(root), tag))
        if apply_:
            raw = p.read_bytes()
            bom = b"\xef\xbb\xbf" if raw.startswith(b"\xef\xbb\xbf") else b""
            p.write_bytes(bom + new.encode("utf-8"))
    for rel, tag in missing:
        print(f"{'ПРИМЕНЕНО' if apply_ else 'нужно'}: {tag:9} {rel}")
    print(f"\nитого: заменено {stats['replaced']}, добавлено {stats['added']}, "
          f"текст-упоминания {stats['text']}, уже в порядке {stats['ok']}")
    if check and missing:
        print("ОШИБКА: есть файлы без заголовка лицензии v2.0", file=sys.stderr)
        return 1
    if not apply_ and not check and missing:
        print("Запустите с --apply, чтобы применить.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
