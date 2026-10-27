#!/usr/bin/env python3
"""Doc freshness for /housekeeper and /rem, read from the Doc Inventory that /ship uses.

Kompara's docs carry no frontmatter. The Doc Inventory in .claude/skills/ship/reviewers/docs.md
maps each doc to the source paths it describes: an entry ending in / is a directory, * stands for
one path segment, anything else is one file. A commit-message line `Doc-Verified: <doc> <commit>`
records that the doc, as <commit> has it, was checked against the code at <commit> and found
accurate. A doc's baseline is the newest of the commit that wrote its current text and the verified
commits at which it already had that text; newest means a descendant first, the later commit time
between unrelated commits. A doc is stale when its sources at the checked commit differ from its
sources at the baseline. Comparing content, not dates or commit lists, also catches a branch merged
after the doc was written and a merge that brings back an older version of a source.

Checks the checkout this file lives in, whatever the current directory:
  doc-freshness.py                          every inventory doc at HEAD, then inventory problems
  doc-freshness.py --diff DOC [COMMIT]      what changed in DOC's sources from its baseline to COMMIT (default HEAD)
  doc-freshness.py --verify DOC COMMIT      record that DOC, as COMMIT has it, matches the code at COMMIT
  doc-freshness.py --apply DOC COMMIT FILE  put FILE in place of DOC, in the working copy and the index
The report and --diff each resolve their commit once and name it: that is the commit to verify.
--verify and --apply refuse unless the index and the working file hold exactly the text COMMIT has,
as a regular file with no symlink above it; --verify also needs HEAD to have that text, and its
commit carries only the Doc-Verified line, never anything staged. Exits 2 when git fails, the
inventory can't be read, or --verify or --apply refuses, having written nothing; exits 3 when
--apply replaced the working copy but couldn't stage it.
"""
import os
import re
import secrets
import stat
import subprocess
import sys
from pathlib import Path

INVENTORY = ".claude/skills/ship/reviewers/docs.md"
TRAILER = "Doc-Verified: "
FILES_SHOWN = 10


def git(root, *args):
    return subprocess.run(
        ["git", "-C", str(root), "-c", "core.quotepath=off", *args], check=True, capture_output=True, text=True
    ).stdout


def rev(root, spec):
    """The object id spec names, such as a commit or the content of a file at a commit, or "" when there is none."""
    return subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", "--quiet", spec], capture_output=True, text=True
    ).stdout.strip()


def holds(root, sha, doc):
    """Whether the index and the working copy both hold doc exactly as commit sha does, as a regular file.

    Compares modes and content ids rather than asking git diff, which trusts assume-unchanged and skip-worktree
    flags. A symlink at the doc or above it fails, so writing the doc can't land in some other file.
    """
    mode, _, blob_id = (git(root, "ls-tree", sha, "--", doc).split() + ["", "", ""])[:3]
    if mode not in ("100644", "100755") or git(root, "ls-files", "--stage", "--", doc).splitlines() != [f"{mode} {blob_id} 0\t{doc}"]:
        return False
    parts = Path(doc).parts
    if any(root.joinpath(*parts[:i]).is_symlink() for i in range(1, len(parts) + 1)) or not (root / doc).is_file():
        return False
    return git(root, "hash-object", "--", doc).strip() == blob_id


class PartlyApplied(Exception):
    """--apply replaced the working copy but couldn't stage it."""


def hash_bytes(root, doc, data, write=False):
    """The id git gives data as doc's content, filters applied; with write, the content is stored too."""
    return subprocess.run(
        ["git", "-C", str(root), "hash-object", *(["-w"] if write else []), "--stdin", f"--path={doc}"],
        input=data, check=True, capture_output=True,
    ).stdout.decode().strip()


def apply(root, doc, sha, new_file):
    """Put new_file in place of doc, in the working copy and the index, or return why not.

    Only while both hold doc exactly as commit sha has it. The walk to the doc follows no symlink, the text about to
    be replaced is checked through an open descriptor, and a newly created file is renamed over it, so the write can't
    land in another file and an edit made meanwhile isn't lost.
    """
    if not holds(root, sha, doc):
        return f"the index or the working copy of {doc} differs from {sha[:7]}'s"
    try:
        data = Path(new_file).read_bytes()
    except OSError as e:
        return f"can't read {new_file}: {e}"
    mode = git(root, "ls-tree", sha, "--", doc).split()[0]
    new_blob = hash_bytes(root, doc, data, write=True)
    *dirs, name = Path(doc).parts
    fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
    try:
        for part in dirs:
            parent, fd = fd, os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(parent)
        with os.fdopen(os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=fd), "rb") as current:
            seen = os.fstat(current.fileno())
            if not stat.S_ISREG(seen.st_mode) or hash_bytes(root, doc, current.read()) != rev(root, f"{sha}:{doc}"):
                return f"{doc} changed before it could be replaced"
        tmp = f".{name}.{secrets.token_hex(8)}.rem"
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW
        out = os.open(tmp, flags, 0o755 if mode == "100755" else 0o644, dir_fd=fd)
        replaced = False
        try:
            with os.fdopen(out, "wb") as f:
                f.write(data)
            now = os.stat(name, dir_fd=fd, follow_symlinks=False)
            if (now.st_dev, now.st_ino, now.st_size, now.st_mtime_ns) != (seen.st_dev, seen.st_ino, seen.st_size, seen.st_mtime_ns):
                return f"{doc} changed before it could be replaced"
            os.replace(tmp, name, src_dir_fd=fd, dst_dir_fd=fd)
            replaced = True
        finally:
            if not replaced:
                os.unlink(tmp, dir_fd=fd)
    except OSError as e:
        return f"couldn't replace {doc}: {e}"
    finally:
        os.close(fd)
    try:
        git(root, "update-index", "--cacheinfo", f"{mode},{new_blob},{doc}")
    except (subprocess.CalledProcessError, OSError) as e:
        detail = e.stderr.strip() if isinstance(e, subprocess.CalledProcessError) else str(e)
        raise PartlyApplied(f"replaced {doc} in the working copy but couldn't stage it: {detail}") from e
    return None


def read_inventory(root):
    """Return ({doc: [source entries]}, [left-out paths]) from the Doc Inventory section."""
    text = (root / INVENTORY).read_text()
    if "\n## Doc Inventory\n" not in text:
        raise ValueError("no '## Doc Inventory' heading")
    section = text.split("\n## Doc Inventory\n", 1)[1].split("\n## ", 1)[0]
    if section.count("```") < 2:
        raise ValueError("no fenced block under the heading")
    _, block, after = section.split("```", 2)
    docs, doc = {}, None
    for line in block.split("\n", 1)[1].splitlines():
        if line.strip() and not line[0].isspace():
            doc = line.strip()
            docs[doc] = []
        elif line.strip() and doc:
            docs[doc] += [s.strip() for s in re.sub(r"^sources:", "", line.strip()).split(",") if s.strip()]
    if not docs:
        raise ValueError("the fenced block lists no docs")
    empty = [d for d, sources in docs.items() if not sources]
    if empty:
        raise ValueError("no sources for " + ", ".join(empty))
    left_out = [p for line in after.splitlines() if line.startswith("Left out on purpose") for p in re.findall(r"`([^`]+)`", line)]
    return docs, left_out


def pathspec(entry):
    """The git pathspec for an inventory entry; git's glob needs ** to reach below a directory written with a wildcard."""
    return ":(glob)" + entry + ("**" if entry.endswith("/") else "")


def dead(root, entry):
    """Why an inventory source entry matches no tracked file under the inventory's rules, or None if it matches some."""
    files = git(root, "ls-files", "--", pathspec(entry)).splitlines()
    if entry.endswith("/") or any(f.count("/") == entry.count("/") for f in files):
        return None if files else "matches no tracked file"
    # git also matches an entry without a trailing / as a directory; the inventory reads it as one file.
    return "names a directory, so it needs a trailing /" if files else "matches no tracked file"


def verifications(root, head):
    """{doc: [verified commit, ...]} from `Doc-Verified: <doc> <commit>` lines in the messages of head's history."""
    found = {}
    for message in git(root, "log", "--format=%B%x00", "--fixed-strings", "--grep=" + TRAILER, head).split("\0"):
        for line in message.splitlines():
            parts = line[len(TRAILER):].rsplit(None, 1) if line.startswith(TRAILER) else []
            if len(parts) == 2 and re.fullmatch(r"[0-9a-f]{7,40}", parts[1]):
                found.setdefault(parts[0], []).append(parts[1])
    return found


def baseline(root, doc, verified, head):
    """The commit whose sources doc's text at head was last written or verified against: (sha, date, kind), or None."""
    current = rev(root, f"{head}:{doc}")
    changed = git(root, "log", "--format=%H", head, "--", doc).split()
    if not current or not changed:
        return None
    wrote = next((c for c in changed if rev(root, f"{c}:{doc}") == current), changed[0])
    # A verification counts only where the doc already had this text; an unknown commit has no blob and drops out.
    checked = [c for c in verified.get(doc, []) if rev(root, f"{c}:{doc}") == current]
    heads = git(root, "merge-base", "--independent", wrote, *checked).split()
    newest = max((line.split() for line in git(root, "log", "--no-walk", "--format=%H %ct %cs", *heads).splitlines()), key=lambda c: int(c[1]))
    return newest[0], newest[2], "changed" if newest[0] == wrote else "verified"


def source_diff(root, base, head, sources, *diff_args):
    return git(root, "diff", "--no-ext-diff", "--no-color", *diff_args, base, head, "--", *map(pathspec, sources))


def since(base):
    sha, date, kind = base
    return f"{date} ({sha[:7]}), " + ("when the doc was last changed" if kind == "changed" else "the commit the doc was last verified against")


def report(root, docs, left_out, head):
    verified = verifications(root, head)
    stale, problems, fresh = [], [], []
    for doc, sources in docs.items():
        problems += [f"DEAD     {doc}: {s} {why}" for s in sources for why in [dead(root, s)] if why]
        base = baseline(root, doc, verified, head)
        if not base:
            problems.append(f"MISSING  {doc}: in the inventory but " + ("not committed yet" if (root / doc).exists() else "not in the repo"))
            continue
        files = [f for f in source_diff(root, base[0], head, sources, "--name-only").splitlines() if f]
        if not files:
            fresh.append(f"FRESH    {doc}: sources unchanged since {since(base)}")
            continue
        stale.append(f"STALE    {doc}: {len(files)} source file{'s' if len(files) > 1 else ''} changed since {since(base)}")
        stale += [f"           {f}" for f in files[:FILES_SHOWN]]
        if len(files) > FILES_SHOWN:
            stale.append(f"           ... and {len(files) - FILES_SHOWN} more files")
    for path in git(root, "ls-files", "--cached", "--others", "--exclude-standard", "--", "docs/").splitlines():
        if path.endswith(".md") and path not in docs and not any(path == p or (p.endswith("/") and path.startswith(p)) for p in left_out):
            problems.append(f"UNMAPPED {path}: in neither the Doc Inventory nor its left-out list")
    print("\n".join(stale + problems + fresh))
    n_stale = sum(line.startswith("STALE") for line in stale)
    print(f"\n{len(docs)} inventory docs: {n_stale} stale, {len(fresh)} fresh. Inventory problems: {len(problems) or 'none'}.")
    print(f"Checked HEAD {head}")


def verify(root, doc, sha):
    """Record a check of doc as commit sha has it, or return why not."""
    text = rev(root, f"{sha}:{doc}")
    if not text or text != rev(root, f"HEAD:{doc}"):
        return f"HEAD's {doc} isn't the text {sha[:7]} has; run --diff again and review that"
    if not holds(root, sha, doc):
        return f"your copy of {doc} differs from {sha[:7]}'s; commit or set those changes aside first"
    git(root, "commit", "--only", "--allow-empty", "-m", f"docs: {doc} still matches the code", "-m", f"{TRAILER}{doc} {sha}")
    print(f"Recorded that {doc} matches the code at {sha[:7]}.")
    return None


def main(args):
    try:
        root = Path(git(Path(__file__).resolve().parent, "rev-parse", "--show-toplevel").strip())
        docs, left_out = read_inventory(root)
        mode, doc, spec, new_file = (args + [None] * 4)[:4]
        arities = {"--diff": (2, 3), "--verify": (3,), "--apply": (4,)}
        if args and (mode not in arities or len(args) not in arities[mode] or doc not in docs):
            print("usage: doc-freshness.py [--diff DOC [COMMIT] | --verify DOC COMMIT | --apply DOC COMMIT FILE], with DOC one of: " + ", ".join(docs), file=sys.stderr)
            return 2
        sha = rev(root, f"{spec or 'HEAD'}^{{commit}}")
        if not sha:
            print(f"doc-freshness: no commit {spec or 'HEAD'}", file=sys.stderr)
            return 2
        if not args:
            report(root, docs, left_out, sha)
            return 0
        if mode in ("--verify", "--apply"):
            refusal = verify(root, doc, sha) if mode == "--verify" else apply(root, doc, sha, new_file)
            if refusal:
                print(f"doc-freshness: {refusal}", file=sys.stderr)
                return 2
            return 0
        base = baseline(root, doc, verifications(root, sha), sha)
        if not base:
            print(f"doc-freshness: {sha[:7]} has no committed {doc}", file=sys.stderr)
            return 2
        diff = source_diff(root, base[0], sha, docs[doc])
        if diff:
            commits = git(root, "log", "--format=%h %cs %s", f"{base[0]}..{sha}", "--", *map(pathspec, docs[doc]))
            print(f"Checked {sha}")
            print(f"Changes to the sources of {doc} since {since(base)}.\n")
            print("Commits that touched them since then (newest first):\n" + (commits or "(none listed: the change arrived in a merge)\n"))
            sys.stdout.write(diff)
        return 0
    except PartlyApplied as e:
        print(f"doc-freshness: {e}", file=sys.stderr)
        return 3
    except subprocess.CalledProcessError as e:
        stderr = e.stderr.decode() if isinstance(e.stderr, bytes) else e.stderr
        print(f"doc-freshness: git failed: {stderr.strip()}", file=sys.stderr)
    except (OSError, ValueError) as e:
        print(f"doc-freshness: can't read the Doc Inventory in {INVENTORY}: {e}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
