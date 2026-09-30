"""Read-only route/settings drift check against a supplied s-ui backend checkout.

Usage: python tools/check_panel_contract.py ../s-ui-main
This checks registries, not protocol semantics or live server behavior.
"""
import argparse
import re
from pathlib import Path


def capture(pattern, text):
    match = re.search(pattern, text, re.S)
    if not match:
        raise ValueError("Source layout changed; review the contract checker")
    return match.group(1)


def compare(label, expected, actual):
    missing, extra = expected - actual, actual - expected
    if missing or extra:
        raise ValueError(f"{label}: missing={sorted(missing)}, extra={sorted(extra)}")
    print(f"PASS {label}: {len(actual)} entries")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("backend", type=Path)
    parser.add_argument("--frontend", type=Path, help="Also compare frontend protocol selectors")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    kotlin = root / "app/src/main/java/com/sonix21/suinode"
    handler = (args.backend / "api/apiV2Handler.go").read_text(encoding="utf-8")
    transport = (kotlin / "data/SuiClient.kt").read_text(encoding="utf-8")
    for method in ("get", "post"):
        # Bound each method to the next function; don't include unrelated switches.
        body = capture(rf"func \(a \*APIv2Handler\) {method}Handler\([^\n]*\) \{{(.*?)(?=\nfunc |\Z)", handler)
        expected = set(re.findall(r'"([A-Za-z0-9]+)"', " ".join(re.findall(r"case ([^:]+):", body))))
        actual = set(re.findall(r'"([A-Za-z0-9]+)"', capture(rf"val {method.upper()}_ACTIONS = setOf\((.*?)\)", transport)))
        compare(f"APIv2 {method.upper()}", expected, actual)
    settings = (args.backend / "service/setting.go").read_text(encoding="utf-8")
    defaults = set(re.findall(r'"(\w+)"\s*:', capture(r"var defaultValueMap = map\[string\]string\{(.*?)\n\}", settings)))
    app_settings = (kotlin / "core/Panel161.kt").read_text(encoding="utf-8")
    protected = set(re.findall(r'"(\w+)"', capture(r"val protectedSettings = setOf\((.*?)\)", app_settings)))
    actual = set(re.findall(r'"(\w+)"', capture(r"val writableSettings = setOf\((.*?)\)", app_settings)))
    compare("Writable settings", defaults - protected, actual)
    if args.frontend:
        registries = [
            ("inbounds", "InTypes", "inbounds/InboundEditorScreen.kt", "IN_TYPES"),
            ("outbounds", "OutTypes", "outbounds/OutboundEditorScreen.kt", "OUT_TYPES"),
            ("dns", "DnsTypes", "dns/DnsScreens.kt", "DNS_TYPES"),
        ]
        for name, ts_name, filename, kt_name in registries:
            ts = (args.frontend / f"src/types/{name}.ts").read_text(encoding="utf-8")
            kt = (kotlin / "ui/screens" / filename).read_text(encoding="utf-8")
            expected = set(re.findall(r":\s*'([^']+)'", capture(rf"export const {ts_name} = \{{(.*?)\}}", ts)))
            actual = set(re.findall(r'"([^"\n]+)"', capture(rf"val {kt_name} = listOf\((.*?)\)", kt)))
            compare(f"Frontend {name} types", expected, actual)
    print("Backend version:", (args.backend / "config/version").read_text().strip())
    print("Registry parity only; run HTTP fixture tests and controlled device tests too.")


if __name__ == "__main__":
    main()
