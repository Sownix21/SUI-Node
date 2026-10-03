"""Small adb UI smoke-test helper. Operates only on emulator-5554, never a phone.

Usage: emulator_ui.py dump | tap LABEL | field LABEL VALUE | screenshot OUTPUT.png
Actions resolve current Android accessibility bounds; failed dumps never reuse stale XML.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SDK = os.environ.get("ANDROID_HOME", os.path.expanduser("~/AppData/Local/Android/Sdk"))
ADB = os.path.join(SDK, "platform-tools", "adb.exe" if os.name == "nt" else "adb")


def adb(*args):
    result = subprocess.run([ADB, "-s", "emulator-5554", *args], capture_output=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    return result.stdout.decode(errors="replace")


def snapshot():
    output = adb("shell", "uiautomator", "dump", "/data/local/tmp/sui-node-ui.xml")
    if "dumped to" not in output:
        raise RuntimeError("No fresh accessibility snapshot: " + output)
    return ET.fromstring(adb("exec-out", "cat", "/data/local/tmp/sui-node-ui.xml"))


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    return str((x1 + x2) // 2), str((y1 + y2) // 2)


def locate(root, label, field=False):
    parents = {child: parent for parent in root.iter() for child in parent}
    matches = [n for n in root.iter("node") if n.get("text") == label or n.get("content-desc") == label]
    if len(matches) != 1:
        raise RuntimeError(f"Expected one visible {label!r}, got {len(matches)}")
    node = matches[0]
    while node in parents:
        if (node.get("class") == "android.widget.EditText" if field else node.get("clickable") == "true"):
            return node
        node = parents[node]
    raise RuntimeError("No actionable control for " + label)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    action = sys.argv[1]
    if action == "screenshot":
        adb("shell", "screencap", "-p", "/data/local/tmp/sui-node-qa.png")
        print(adb("pull", "/data/local/tmp/sui-node-qa.png", sys.argv[2]))
    else:
        root = snapshot()
        if action == "dump":
            for node in root.iter("node"):
                if node.get("text") or node.get("content-desc"):
                    print(node.get("text") or node.get("content-desc"), node.get("bounds"))
        elif action in ("tap", "field"):
            node = locate(root, sys.argv[2], field=action == "field")
            adb("shell", "input", "tap", *center(node))
            if action == "field":
                value = sys.argv[3]
                if not value.isascii() or " " in value:
                    raise ValueError("Fixture helper accepts non-space ASCII values only")
                adb("shell", "input", "keycombination", "113", "29")
                adb("shell", "input", "keyevent", "67")
                for character in value:
                    adb("shell", "input", "text", character)
                    time.sleep(0.12)
                adb("shell", "input", "keyevent", "4")
            print("Completed", action, sys.argv[2])
        else:
            raise ValueError("Unknown action")
