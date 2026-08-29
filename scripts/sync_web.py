#!/usr/bin/env python3
"""
sync_web.py — Desktop-to-Web Synchronization & Drift Tracking Tool

Ensures full parity between Desktop Liquid LSD assets/shaders/math logic
and the standalone WebGL2 Web TV client (web/).
"""

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

# Resolve project root relative to this script
PROJECT_ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = PROJECT_ROOT / "web" / "sync_manifest.json"

# ANSI Color formatting
USE_COLOR = sys.stdout.isatty()
COLOR_GREEN = "\033[92m" if USE_COLOR else ""
COLOR_RED = "\033[91m" if USE_COLOR else ""
COLOR_YELLOW = "\033[93m" if USE_COLOR else ""
COLOR_CYAN = "\033[96m" if USE_COLOR else ""
COLOR_BOLD = "\033[1m" if USE_COLOR else ""
COLOR_RESET = "\033[0m" if USE_COLOR else ""


def compute_sha256(filepath: Path) -> str:
    """Compute SHA-256 hash of a file."""
    if not filepath.exists():
        return ""
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def transpile_shader_to_webgl2(source_text: str) -> str:
    """
    Transpiles a desktop GLSL shader (OpenGL 3.3 Core) to WebGL2 (GLSL ES 3.00).
    Converts #version 330 core -> #version 300 es + precision highp float;
    """
    # Normalize line endings to LF
    text = source_text.replace("\r\n", "\n")

    # Replace #version 330 [core] with #version 300 es
    if re.search(r"^#version\s+330(\s+core)?", text, re.MULTILINE):
        text = re.sub(
            r"^#version\s+330(\s+core)?",
            "#version 300 es\nprecision highp float;",
            text,
            flags=re.MULTILINE,
        )
    elif not text.startswith("#version 300 es"):
        text = "#version 300 es\nprecision highp float;\n" + text
    elif not re.search(r"precision\s+(highp|mediump|lowp)\s+float;", text):
        text = text.replace(
            "#version 300 es\n", "#version 300 es\nprecision highp float;\n"
        )

    # Ensure trailing newline
    return text.strip() + "\n"


def load_manifest() -> dict:
    if not MANIFEST_PATH.exists():
        print(f"{COLOR_RED}Error: Manifest not found at {MANIFEST_PATH}{COLOR_RESET}", file=sys.stderr)
        sys.exit(1)
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_manifest(manifest_data: dict):
    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest_data, f, indent=2)
        f.write("\n")


def check_sync(manifest: dict) -> dict:
    """
    Checks sync state across all shaders and monitored files.
    Returns structured results.
    """
    results = {
        "in_sync": [],
        "out_of_sync": [],
        "missing": [],
        "total_checked": 0,
    }

    # 1. Check Shaders
    for shader_entry in manifest.get("shaders", []):
        results["total_checked"] += 1
        src_rel = shader_entry["desktop"]
        dst_rel = shader_entry["web"]
        desc = shader_entry.get("description", "")

        src_path = PROJECT_ROOT / src_rel
        dst_path = PROJECT_ROOT / dst_rel

        if not src_path.exists():
            results["missing"].append({
                "type": "shader",
                "mode": "auto_shader",
                "source": src_rel,
                "target": dst_rel,
                "reason": f"Desktop source file missing: {src_rel}",
                "description": desc,
            })
            continue

        if not dst_path.exists():
            results["out_of_sync"].append({
                "type": "shader",
                "mode": "auto_shader",
                "source": src_rel,
                "target": dst_rel,
                "reason": f"Web shader missing: {dst_rel}",
                "action": f"Run './scripts/sync_web.py --apply' to generate {dst_rel}",
                "description": desc,
            })
            continue

        # Transpile desktop content and compare with existing web shader
        desktop_raw = src_path.read_text(encoding="utf-8")
        expected_web = transpile_shader_to_webgl2(desktop_raw)
        current_web = dst_path.read_text(encoding="utf-8").replace("\r\n", "\n")

        if expected_web == current_web:
            results["in_sync"].append({
                "type": "shader",
                "source": src_rel,
                "target": dst_rel,
                "description": desc,
            })
        else:
            results["out_of_sync"].append({
                "type": "shader",
                "mode": "auto_shader",
                "source": src_rel,
                "target": dst_rel,
                "reason": "Desktop shader content differs from web shader",
                "action": f"Run './scripts/sync_web.py --apply' to update {dst_rel}",
                "description": desc,
            })

    # 2. Check Monitored Sources (Kotlin/Math/Serializer logic)
    for src_entry in manifest.get("monitored_sources", []):
        results["total_checked"] += 1
        src_rel = src_entry["desktop"]
        dst_rel = src_entry["web"]
        desc = src_entry.get("description", "")
        last_hash = src_entry.get("last_synced_hash", "")

        src_path = PROJECT_ROOT / src_rel
        dst_path = PROJECT_ROOT / dst_rel

        if not src_path.exists():
            results["missing"].append({
                "type": "manual_review",
                "source": src_rel,
                "target": dst_rel,
                "reason": f"Desktop source file missing: {src_rel}",
                "description": desc,
            })
            continue

        current_hash = compute_sha256(src_path)

        if not last_hash or current_hash != last_hash:
            results["out_of_sync"].append({
                "type": "manual_review",
                "mode": "manual_review",
                "source": src_rel,
                "target": dst_rel,
                "reason": f"Desktop code changed (hash {current_hash[:8]} != recorded {last_hash[:8] if last_hash else 'NONE'})",
                "action": f"Verify JS logic parity in {dst_rel}, then run './scripts/sync_web.py --mark-synced {src_rel}'",
                "description": desc,
                "current_hash": current_hash,
            })
        else:
            results["in_sync"].append({
                "type": "manual_review",
                "source": src_rel,
                "target": dst_rel,
                "description": desc,
            })

    return results


def apply_sync(manifest: dict) -> list:
    """
    Applies automated transpilation for all shaders.
    Returns list of updated files.
    """
    updated = []
    for shader_entry in manifest.get("shaders", []):
        src_rel = shader_entry["desktop"]
        dst_rel = shader_entry["web"]

        src_path = PROJECT_ROOT / src_rel
        dst_path = PROJECT_ROOT / dst_rel

        if not src_path.exists():
            print(f"{COLOR_YELLOW}Warning: Source not found: {src_rel}{COLOR_RESET}")
            continue

        desktop_raw = src_path.read_text(encoding="utf-8")
        web_code = transpile_shader_to_webgl2(desktop_raw)

        dst_path.parent.mkdir(parents=True, exist_ok=True)
        current_code = dst_path.read_text(encoding="utf-8") if dst_path.exists() else None

        if current_code != web_code:
            dst_path.write_text(web_code, encoding="utf-8")
            updated.append(dst_rel)

    return updated


def mark_synced(manifest: dict, target: str) -> bool:
    """
    Updates the recorded hash for one or all monitored sources.
    """
    modified = False
    for src_entry in manifest.get("monitored_sources", []):
        src_rel = src_entry["desktop"]
        if target == "all" or target == src_rel or target in src_rel:
            src_path = PROJECT_ROOT / src_rel
            if src_path.exists():
                new_hash = compute_sha256(src_path)
                src_entry["last_synced_hash"] = new_hash
                print(f"{COLOR_GREEN}Updated sync hash for {src_rel} -> {new_hash[:12]}...{COLOR_RESET}")
                modified = True
            else:
                print(f"{COLOR_RED}Cannot hash missing file: {src_rel}{COLOR_RESET}", file=sys.stderr)

    if modified:
        save_manifest(manifest)
    return modified


def main():
    parser = argparse.ArgumentParser(
        description="Liquid LSD Desktop-to-Web Synchronization & Drift Checker"
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Check sync status without modifying files (exits 1 if drift detected)",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Auto-transpile modified desktop shaders and write to web/shaders/",
    )
    parser.add_argument(
        "--mark-synced",
        metavar="TARGET",
        help="Update last_synced_hash in manifest for a verified desktop source (or 'all')",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Output check results in machine-readable JSON format",
    )

    args = parser.parse_args()
    manifest = load_manifest()

    if args.mark_synced:
        if mark_synced(manifest, args.mark_synced):
            print(f"{COLOR_GREEN}Successfully updated {MANIFEST_PATH.relative_to(PROJECT_ROOT)}{COLOR_RESET}")
            sys.exit(0)
        else:
            print(f"{COLOR_YELLOW}No matching monitored sources found for '{args.mark_synced}'{COLOR_RESET}")
            sys.exit(1)

    if args.apply:
        updated = apply_sync(manifest)
        if updated:
            print(f"{COLOR_GREEN}{COLOR_BOLD}Successfully synchronized {len(updated)} web shader(s):{COLOR_RESET}")
            for u in updated:
                print(f"  {COLOR_GREEN}✓{COLOR_RESET} {u}")
        else:
            print(f"{COLOR_GREEN}All shaders are already in sync with desktop sources.{COLOR_RESET}")

    # If --check or default (no args)
    if args.check or (not args.apply and not args.mark_synced):
        results = check_sync(manifest)

        if args.json:
            print(json.dumps(results, indent=2))
            sys.exit(0 if not results["out_of_sync"] and not results["missing"] else 1)

        print("\n" + "=" * 80)
        print(f"{COLOR_BOLD}{COLOR_CYAN}                     LIQUID LSD DESKTOP ↔ WEB SYNC REPORT{COLOR_RESET}")
        print("=" * 80)

        num_out = len(results["out_of_sync"])
        num_missing = len(results["missing"])
        num_in = len(results["in_sync"])

        if num_out == 0 and num_missing == 0:
            print(f"\n{COLOR_GREEN}{COLOR_BOLD}✓ ALL {results['total_checked']} TRACKED ASSETS & SHADERS ARE 100% IN SYNC!{COLOR_RESET}\n")
            print("=" * 80)
            sys.exit(0)

        print(f"\n{COLOR_RED}{COLOR_BOLD}[!] {num_out + num_missing} file(s) require attention between Desktop and Web:{COLOR_RESET}\n")

        idx = 1
        for item in results["out_of_sync"]:
            badge = f"{COLOR_YELLOW}[AUTO-FIXABLE SHADER]{COLOR_RESET}" if item.get("mode") == "auto_shader" else f"{COLOR_RED}[MANUAL REVIEW / MATH]{COLOR_RESET}"
            print(f"{COLOR_BOLD}{idx}. {badge} {item.get('description', '')}{COLOR_RESET}")
            print(f"   Desktop Source : {COLOR_CYAN}{item['source']}{COLOR_RESET}")
            print(f"   Web Target     : {COLOR_CYAN}{item['target']}{COLOR_RESET}")
            print(f"   Status         : {item['reason']}")
            print(f"   Action         : {COLOR_BOLD}{item['action']}{COLOR_RESET}\n")
            idx += 1

        for item in results["missing"]:
            print(f"{COLOR_BOLD}{idx}. {COLOR_RED}[MISSING SOURCE]{COLOR_RESET} {item.get('description', '')}")
            print(f"   Path           : {COLOR_RED}{item['source']}{COLOR_RESET}")
            print(f"   Status         : {item['reason']}\n")
            idx += 1

        print("=" * 80)
        print(f"Summary: {COLOR_GREEN}{num_in} in sync{COLOR_RESET}, {COLOR_YELLOW if num_out else COLOR_RESET}{num_out} out of sync{COLOR_RESET}, {COLOR_RED if num_missing else COLOR_RESET}{num_missing} missing{COLOR_RESET}.")
        print("=" * 80 + "\n")
        sys.exit(1)


if __name__ == "__main__":
    main()
