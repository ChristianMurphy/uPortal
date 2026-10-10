#!/usr/bin/env python3
"""Compares an upstream jar with a jar built from its imported source.

usage: python3 -I compare-classes.py <upstream.jar> <built.jar>

Prints, for the resources of the upstream jar (everything but classes and Maven's META-INF
files): those missing from the built jar or with different content (a difference only in the
final newline is reported as such).

Then, for the classes of the upstream jar:
  * classes only in one jar;
  * lines that differ in `javap -protected -s` (the public and protected API) and in
    `javap -private -s` (every member);
  * the class file versions;
  * methods whose bytecode differs (`javap -c`, constant-pool indexes and offsets removed), and
    how many of those differ only in ways a newer javac explains:
      - branch targets and exception-table offsets that moved because other code changed size,
      - a checkcast after a call that returns a type variable,
      - Object methods (toString, getClass, ...) called through the receiver's static type,
      - adjacent string literals folded into one constant, ldc_w instead of ldc,
      - enum static initialisers that call the synthetic $values() (javac 15+).
Needs a JDK's javap on the PATH.
"""
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def extract(jar, into):
    with zipfile.ZipFile(jar) as z:
        names = [n for n in z.namelist() if n.endswith(".class")]
        z.extractall(into, names)
    return sorted(n[: -len(".class")].replace("/", ".") for n in names)


def javap(classpath, classes, *options):
    out = subprocess.run(
        ["javap", *options, "-cp", str(classpath), *classes],
        capture_output=True, text=True, check=True,
    ).stdout
    return [l for l in out.splitlines() if not l.startswith("Compiled from")]


def methods(lines):
    """Maps 'class header + method signature' to the method's normalised instructions."""
    result, cls, current = {}, None, None
    for line in lines:
        if re.match(r"^\S.*\{$", line):
            cls = re.sub(r"^final ", "", line.strip())  # javac 9+ drops final on anonymous classes
        elif line.startswith("  ") and not line.startswith("    ") and line.strip() not in ("", "Code:"):
            current = cls + " " + line.strip()
            result[current] = []
        elif current and line.startswith("    "):
            text = re.sub(r"#\d+(, *\d+)?", "", line).strip()
            text = re.sub(r"^\d+: ", "", text)
            if re.match(r"^\d+ +\d+ +\d+ +(Class|any)", text):  # exception table entry
                continue
            result[current].append(re.sub(r"\s+", " ", text).replace("ldc_w ", "ldc "))
    return result


def explained(method, upstream, built):
    def normalise(code):
        kept = []
        for line in code:
            if line.startswith("checkcast"):
                continue
            line = re.sub(r"^(if\w*|goto|goto_w|jsr) \d+$", r"\1", line)
            line = re.sub(r"^invoke(virtual|interface) // (Interface)?Method \S+\.(toString|getClass|hashCode|equals):",
                          r"invoke* \3:", line)
            kept.append(line)
        text = "\n".join(kept)
        append = "\ninvokevirtual // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;\n"
        text = re.sub(r"ldc // String [^\n]*(" + re.escape(append) + r"ldc // String [^\n]*)+", "ldc // String *", text)
        return re.sub(r"ldc // String [^\n]*", "ldc // String *", text)

    if normalise(upstream) == normalise(built):
        return True
    # An enum's static initialiser: javac 15+ builds the values array in $values()
    return (" extends java.lang.Enum<" in method and method.endswith(" static {};")
            and any("Method $values:" in l for l in built) and not any("$values" in l for l in upstream))


def compare_resources(upstream_jar, built_jar):
    skip = re.compile(r"(\.class$|/$|^META-INF/(maven/|MANIFEST\.MF$|DEPENDENCIES$))")
    with zipfile.ZipFile(upstream_jar) as u, zipfile.ZipFile(built_jar) as b:
        names = [n for n in u.namelist() if not skip.search(n)]
        built = set(b.namelist())
        differing = 0
        for name in names:
            if name not in built:
                print(f"  resource missing from built jar: {name}")
                differing += 1
                continue
            a, z = u.read(name), b.read(name)
            if a != z:
                kind = "final newline only" if a.rstrip(b"\r\n") == z.rstrip(b"\r\n") else "content"
                print(f"  resource differs ({kind}): {name}")
                differing += kind == "content"
        print(f"resources: {len(names)}, differing in content {differing}")
        return differing


def main(upstream_jar, built_jar):
    resource_differences = compare_resources(upstream_jar, built_jar)
    with tempfile.TemporaryDirectory() as tmp:
        u, b = Path(tmp, "upstream"), Path(tmp, "built")
        u_classes, b_classes = extract(upstream_jar, u), extract(built_jar, b)
        print(f"classes: upstream {len(u_classes)}, built {len(b_classes)}")
        for name in sorted(set(u_classes) ^ set(b_classes)):
            print(f"  only in {'upstream' if name in u_classes else 'built'}: {name}")
        common = [c for c in u_classes if c in b_classes]
        for level in ("protected", "private"):
            a, z = javap(u, common, f"-{level}", "-s"), javap(b, common, f"-{level}", "-s")
            diff = sorted(set(a) ^ set(z))
            print(f"javap -{level} -s: {len(diff)} differing lines")
            for line in diff:
                print(f"  {'-' if line in a else '+'} {line.strip()}")
        for name, root in (("upstream", u), ("built", b)):
            versions = {}
            for f in root.rglob("*.class"):
                major = int.from_bytes(f.read_bytes()[6:8], "big")
                versions[major] = versions.get(major, 0) + 1
            print(f"class file versions ({name}): {versions}")
        um, bm = methods(javap(u, common, "-p", "-c")), methods(javap(b, common, "-p", "-c"))
        differing = [m for m in um if m in bm and um[m] != bm[m]]
        unexplained = [m for m in differing if not explained(m, um[m], bm[m])]
        print(f"methods: upstream {len(um)}, built {len(bm)}, identical {len(um) - len(differing) - len(set(um) - set(bm))}, "
              f"differing {len(differing)} (unexplained {len(unexplained)})")
        for m in sorted(set(um) ^ set(bm)):
            print(f"  only in {'upstream' if m in um else 'built'}: {m}")
        for m in unexplained:
            print(f"  unexplained: {m}")
        return 1 if unexplained or resource_differences else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
