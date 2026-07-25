#!/usr/bin/env python3
"""Lightweight repository audit for GoalZone PlantUML class/sequence diagrams."""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parent
CLASS_DIR = ROOT / "Class Diagram"
SEQUENCE_DIR = ROOT / "Sequence"


def aliases(text: str) -> set[str]:
    result: set[str] = set()
    pattern = re.compile(r'^\s*(?:actor|participant|database|boundary|control|entity|collections|queue)\s+(?:"[^"]+"\s+as\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*$')
    for line in text.splitlines():
        match = pattern.match(line)
        if match:
            result.add(match.group(1))
    return result


def audit_sequence(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    if text.count("@startuml") != 1 or text.count("@enduml") != 1:
        errors.append("must contain exactly one @startuml/@enduml pair")
    known = aliases(text)
    depth: Counter[str] = Counter()
    for line_number, raw in enumerate(text.splitlines(), start=1):
        line = raw.strip()
        arrow = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)\s+[-.]+>+\s+([A-Za-z_][A-Za-z0-9_]*)\s*:", line)
        if arrow:
            for endpoint in arrow.groups():
                if endpoint not in known:
                    errors.append(f"line {line_number}: arrow uses undeclared lifeline {endpoint}")
        match = re.fullmatch(r"activate\s+([A-Za-z_][A-Za-z0-9_]*)", line)
        if match:
            name = match.group(1)
            if name not in known:
                errors.append(f"line {line_number}: activate unknown lifeline {name}")
            depth[name] += 1
            continue
        match = re.fullmatch(r"deactivate\s+([A-Za-z_][A-Za-z0-9_]*)", line)
        if match:
            name = match.group(1)
            depth[name] -= 1
            if depth[name] < 0:
                errors.append(f"line {line_number}: deactivate {name} without matching activation")
                depth[name] = 0
    for name, count in sorted(depth.items()):
        if count:
            errors.append(f"unbalanced activation for {name}: {count} bar(s) remain")
    if "skinparam monochrome true" not in text:
        errors.append("missing monochrome template styling")
    return errors


def audit_class(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []
    if text.count("@startuml") != 1 or text.count("@enduml") != 1:
        errors.append("must contain exactly one @startuml/@enduml pair")
    if "skinparam classAttributeIconSize 0" not in text:
        errors.append("missing class template styling")
    if not re.search(r"\b(class|interface|enum)\s+", text):
        errors.append("contains no class/interface/enum declarations")
    if re.search(r"<<[^>]*(mock|fake)[^>]*>>", text, flags=re.IGNORECASE):
        errors.append("contains mock/fake element")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-class", type=int, default=64)
    parser.add_argument("--expected-sequence", type=int, default=64)
    args = parser.parse_args()

    class_files = sorted(CLASS_DIR.glob("*.puml"))
    sequence_files = sorted(SEQUENCE_DIR.glob("*.puml"))
    failures: list[str] = []
    for path in class_files:
        failures.extend(f"{path.relative_to(ROOT)}: {error}" for error in audit_class(path))
    for path in sequence_files:
        failures.extend(f"{path.relative_to(ROOT)}: {error}" for error in audit_sequence(path))
    if len(class_files) != args.expected_class:
        failures.append(f"expected {args.expected_class} class diagrams, found {len(class_files)}")
    if len(sequence_files) != args.expected_sequence:
        failures.append(f"expected {args.expected_sequence} sequence diagrams, found {len(sequence_files)}")

    print(f"Class diagrams: {len(class_files)}")
    print(f"Sequence diagrams: {len(sequence_files)}")
    if failures:
        print("Audit failures:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("PlantUML structure and activation bars: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
