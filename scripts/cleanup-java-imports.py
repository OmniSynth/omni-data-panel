#!/usr/bin/env python3
"""清理 Java 多余 import：通配符、同包、重复、未使用；并补全缺失的本项目类型 import。"""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "server" / "src"
STAR_ROOT = "com.omni.panel."

PACKAGE_RE = re.compile(r"^package\s+([\w.]+)\s*;", re.M)
# 允许通配符，例如 org.quartz.* / org.springframework.web.bind.annotation.*
IMPORT_RE = re.compile(r"^import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;\s*$")
TYPE_DECL_RE = re.compile(
    r"^\s*(?:public\s+|protected\s+|private\s+)?(?:static\s+)?(?:final\s+)?"
    r"(?:class|interface|enum|record)\s+(\w+)",
    re.M,
)


def collect_type_index() -> dict[str, list[str]]:
    index: dict[str, list[str]] = defaultdict(list)
    for path in ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        pkg_m = PACKAGE_RE.search(text)
        if not pkg_m:
            continue
        pkg = pkg_m.group(1)
        for name in TYPE_DECL_RE.findall(text):
            fqcn = f"{pkg}.{name}"
            if fqcn not in index[name]:
                index[name].append(fqcn)
    return index


def strip_comments_and_strings(src: str) -> str:
    out: list[str] = []
    i = 0
    n = len(src)
    while i < n:
        if i + 1 < n and src[i : i + 2] == "//":
            while i < n and src[i] != "\n":
                i += 1
            continue
        if i + 1 < n and src[i : i + 2] == "/*":
            i += 2
            while i + 1 < n and src[i : i + 2] != "*/":
                i += 1
            i = min(i + 2, n)
            continue
        ch = src[i]
        if ch in ('"', "'"):
            quote = ch
            out.append(" ")
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def split_java(text: str) -> tuple[str, list[str], str]:
    lines = text.splitlines(keepends=True)
    pkg_idx = next((i for i, ln in enumerate(lines) if ln.strip().startswith("package ")), None)
    if pkg_idx is None:
        return "", [], text

    header = "".join(lines[: pkg_idx + 1])
    i = pkg_idx + 1
    imports: list[str] = []
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped == "":
            i += 1
            continue
        if stripped.startswith("import "):
            imports.append(stripped if stripped.endswith(";") else stripped + ";")
            i += 1
            continue
        break
    body = "".join(lines[i:]).lstrip("\n")
    return header, imports, body


def parse_import(line: str) -> tuple[bool, str] | None:
    m = IMPORT_RE.match(line.strip())
    if not m:
        return None
    return bool(m.group(1)), m.group(2)


def simple_name(fqcn: str) -> str:
    return fqcn.split(".")[-1]


def is_used(name: str, body_plain: str) -> bool:
    return re.search(rf"(?<![\w.]){re.escape(name)}(?!\w)", body_plain) is not None


def organize_file(path: Path, type_index: dict[str, list[str]]) -> bool:
    original = path.read_text(encoding="utf-8")
    pkg_m = PACKAGE_RE.search(original)
    if not pkg_m:
        return False
    pkg = pkg_m.group(1)

    header, import_lines, body = split_java(original)
    if not header:
        return False

    kept: list[tuple[bool, str]] = []
    seen: set[tuple[bool, str]] = set()

    for line in import_lines:
        parsed = parse_import(line)
        if not parsed:
            continue
        is_static, fqcn = parsed
        if not is_static and fqcn.endswith(".*") and fqcn.startswith(STAR_ROOT):
            continue
        if not is_static and not fqcn.endswith(".*") and fqcn.rsplit(".", 1)[0] == pkg:
            continue
        key = (is_static, fqcn)
        if key in seen:
            continue
        seen.add(key)
        kept.append(key)

    body_plain = strip_comments_and_strings(body)

    filtered: list[tuple[bool, str]] = []
    for is_static, fqcn in kept:
        if is_static or fqcn.endswith(".*"):
            filtered.append((is_static, fqcn))
            continue
        if is_used(simple_name(fqcn), body_plain):
            filtered.append((is_static, fqcn))
    kept = filtered
    seen = set(kept)

    imported_names = {
        simple_name(fqcn) for is_static, fqcn in kept if not is_static and not fqcn.endswith(".*")
    }
    same_pkg_types = {
        name
        for name, fqcns in type_index.items()
        for fqcn in fqcns
        if fqcn.rsplit(".", 1)[0] == pkg
    }

    for name, fqcns in type_index.items():
        if name in imported_names or name in same_pkg_types:
            continue
        if not is_used(name, body_plain):
            continue
        chosen = None
        if len(fqcns) == 1:
            chosen = fqcns[0]
        else:
            preferred = [f for f in fqcns if f.startswith(STAR_ROOT)]
            if len(preferred) == 1:
                chosen = preferred[0]
        if chosen is None:
            continue
        key = (False, chosen)
        if key not in seen:
            kept.append(key)
            seen.add(key)

    def sort_key(item: tuple[bool, str]):
        is_static, fqcn = item
        if fqcn.startswith("java."):
            group = 0
        elif fqcn.startswith("javax.") or fqcn.startswith("jakarta."):
            group = 1
        elif fqcn.startswith("com.omni."):
            group = 3
        else:
            group = 2
        return (0 if is_static else 1, group, fqcn)

    kept_sorted = sorted(kept, key=sort_key)
    import_block = "".join(
        f"import {'static ' if s else ''}{fqcn};\n" for s, fqcn in kept_sorted
    )
    new_text = header.rstrip() + "\n\n"
    if import_block:
        new_text += import_block + "\n"
    new_text += body
    if not new_text.endswith("\n"):
        new_text += "\n"

    if new_text != original.replace("\r\n", "\n"):
        # 统一用 \n，避免仅换行符差异导致误写；比较时忽略 CRLF
        with path.open("w", encoding="utf-8", newline="\n") as fh:
            fh.write(new_text)
        return True
    return False


def main() -> int:
    type_index = collect_type_index()
    changed = 0
    files = sorted(ROOT.rglob("*.java"))
    for path in files:
        if organize_file(path, type_index):
            changed += 1
            rel = path.relative_to(ROOT.parents[1])
            print(f"updated: {rel}")
    print(f"done. files changed: {changed}/{len(files)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
