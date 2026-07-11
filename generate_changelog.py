"""Generate a CHANGELOG.md entry for a release from git history.

This script runs automatically inside `./gradlew release` (the `generateAIChangelog`
task, wired into `beforeReleaseBuild`). It collects the commits since the last tag,
pre-categorizes them, optionally asks an LLM to write a polished changelog section,
and inserts that section into CHANGELOG.md (below the title, Keep a Changelog style).

DATA-SHARING NOTICE
-------------------
When AI generation is enabled (an OpenAI token is present and CHANGELOG_SKIP_AI is
not set), this script sends commit messages, commit bodies, file lists and code
*diffs* for the release range to the OpenAI API. Diffs can contain sensitive logic
or, in rare cases, secrets. This is an intentional trade-off for output quality on a
security-focused library. To avoid sending diffs, run with CHANGELOG_SKIP_AI=1 (uses
the deterministic offline generator) or set CHANGELOG_NO_DIFFS=1 (AI on, but only
commit messages/bodies and file lists are sent, no diff bodies).

The changelog step is best-effort: any failure to reach the AI degrades to the
deterministic generator so a release is never aborted by this script.

Environment variables
---------------------
  OPENAI_API_TOKEN            OpenAI API key. If unset, the deterministic generator is used.
  OPENAI_MODEL               Model name (default: gpt-5).
  OPENAI_TEMPERATURE         Sampling temperature (default: 0.2). Ignored gracefully
                             if the model rejects a custom temperature.
  CHANGELOG_SKIP_AI          If set (to any non-empty value), skip the AI entirely.
  CHANGELOG_NO_DIFFS         If set, send commit metadata to the AI but no diff bodies.
  CHANGELOG_PROMPT_CHAR_BUDGET  Max characters of commit context sent to the AI
                             (default: 200000). Context degrades gracefully when over.
  CHANGELOG_FETCH_PR_BODIES  If set to 0/false, skip fetching PR descriptions via `gh`.

Usage
-----
  python generate_changelog.py <version> [--dry-run]
  python generate_changelog.py            # interactive (prompts for version on a TTY)
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import date

DEFAULT_MODEL = os.environ.get("OPENAI_MODEL", "gpt-5")
DEFAULT_TEMPERATURE = os.environ.get("OPENAI_TEMPERATURE", "0.2")
PROMPT_CHAR_BUDGET = int(os.environ.get("CHANGELOG_PROMPT_CHAR_BUDGET", "200000"))
PER_COMMIT_DIFF_LINE_LIMIT = 500
MAX_PR_BODY_CHARS = 4000
EXEMPLAR_CHAR_LIMIT = 6000
CHANGELOG_FILE = "CHANGELOG.md"

CATEGORY_ORDER = [
    ("security", "Security"),
    ("breaking_changes", "Breaking Changes"),
    ("features", "Features"),
    ("fixes", "Fixes"),
    ("refactorings", "Refactoring"),
    ("docs", "Documentation"),
    ("tests", "Testing"),
    ("other", "Other Changes"),
]

# Commits that are pure release plumbing and carry no changelog value.
RELEASE_PLUMBING_RE = re.compile(
    r"\[Gradle Release Plugin\]|new version commit|pre tag commit|-SNAPSHOT",
    re.IGNORECASE,
)
PR_NUMBER_RE = re.compile(r"#(\d+)")
VERSION_HEADING_RE = re.compile(r"^## \[([^\]]+)\]")
MERGE_COMMIT_RE = re.compile(r"^Merge (pull request|branch|remote-tracking)", re.IGNORECASE)


# --------------------------------------------------------------------------- #
# Git collection
# --------------------------------------------------------------------------- #
def _git(args):
    """Run a git command, decoding as UTF-8 and tolerating undecodable bytes."""
    return subprocess.check_output(args, encoding="utf-8", errors="replace")


def get_last_reference():
    """Return the last tag, or the first commit if there are no tags."""
    try:
        return _git(["git", "describe", "--tags", "--abbrev=0"]).strip()
    except subprocess.CalledProcessError:
        print("No tags found. Using the first commit as reference.")
        return _git(["git", "rev-list", "--max-parents=0", "HEAD"]).strip()


def _parse_stat_files(show_output):
    """Extract changed file paths from the diffstat block of `git show --stat`."""
    files = []
    for line in show_output.splitlines():
        # Diffstat rows look like: " path/to/File.java | 12 +++---"
        m = re.match(r"^\s(\S.*?)\s+\|\s+(?:\d+|Bin)", line)
        if m:
            path = m.group(1).strip()
            # Handle rename rows: "old => new" or "dir/{a => b}/file".
            if "=>" in path:
                path = re.sub(r"\{[^}]*=>\s*([^}]*)\}", r"\1", path)
                path = path.split("=>")[-1].strip()
            files.append(path)
    return files


def get_commits(last_ref):
    """Collect commits since `last_ref`, with subject, body, diff and file list.

    Release-plumbing commits are filtered out.
    """
    us, rs = "\x1f", "\x1e"  # unit + record separators (safe for multiline bodies)
    raw = _git(
        ["git", "log", f"{last_ref}..HEAD", f"--pretty=format:%H{us}%s{us}%b{rs}"]
    )
    if not raw.strip():
        return []

    commits = []
    for record in raw.split(rs):
        record = record.strip("\n")
        if not record.strip():
            continue
        parts = record.split(us)
        commit_hash = parts[0].strip()
        subject = parts[1] if len(parts) > 1 else ""
        body = parts[2].strip() if len(parts) > 2 else ""
        if not commit_hash:
            continue

        if RELEASE_PLUMBING_RE.search(subject) or (
            body and RELEASE_PLUMBING_RE.search(body) and not subject.strip()
        ):
            continue

        # One `git show` yields both the diffstat (file summary) and the patch.
        show = _git(["git", "show", "--stat", "--patch", commit_hash])
        pr_numbers = sorted({int(n) for n in PR_NUMBER_RE.findall(f"{subject}\n{body}")})

        commits.append(
            {
                "hash": commit_hash,
                "subject": subject,
                "body": body,
                "diff": show,
                "files": _parse_stat_files(show),
                "pr_numbers": pr_numbers,
                "pr_bodies": [],
            }
        )
    return commits


def fetch_pr_bodies(commits):
    """Best-effort: attach PR titles/descriptions for referenced PRs via `gh`.

    Silently no-ops if `gh` is unavailable or a call fails.
    """
    if os.environ.get("CHANGELOG_FETCH_PR_BODIES", "").lower() in ("0", "false", "no"):
        return
    if not shutil.which("gh"):
        return

    cache = {}
    for commit in commits:
        for number in commit["pr_numbers"]:
            if number not in cache:
                cache[number] = _fetch_single_pr(number)
            if cache[number]:
                commit["pr_bodies"].append(cache[number])


def _fetch_single_pr(number):
    try:
        out = subprocess.run(
            ["gh", "pr", "view", str(number), "--json", "title,body"],
            capture_output=True,
            encoding="utf-8",
            errors="replace",
            timeout=30,
        )
    except (subprocess.SubprocessError, OSError):
        return None
    if out.returncode != 0:
        return None
    try:
        data = json.loads(out.stdout)
    except ValueError:
        return None
    title = (data.get("title") or "").strip()
    body = (data.get("body") or "").strip()
    if not title and not body:
        return None
    if len(body) > MAX_PR_BODY_CHARS:
        body = body[:MAX_PR_BODY_CHARS] + "\n... (PR description truncated)"
    return f"#{number} {title}\n{body}".strip()


# --------------------------------------------------------------------------- #
# Categorization
# --------------------------------------------------------------------------- #
def _looks_like_test(path):
    p = path.lower()
    return (
        "/test/" in p
        or "/tests/" in p
        or p.endswith(("test.java", "tests.java", "spec.js", "spec.ts"))
        or ".test." in p
        or ".spec." in p
    )


def _looks_like_docs(path):
    p = path.lower()
    return p.endswith((".md", ".adoc", ".rst", ".txt")) or "/docs/" in p


def categorize_commits(commits):
    """Bucket commits by conventional-commit prefix, then by changed-file paths.

    No whole-diff substring guessing: leftover commits are classified by the kind
    of files they touch (docs-only, tests-only) or left as "other".
    """
    categories = {key: [] for key, _ in CATEGORY_ORDER}

    for commit in commits:
        subject = commit["subject"].lower()
        prefix = subject.split(":", 1)[0]
        body = commit["body"].lower()
        conventional_breaking = bool(re.match(r"^[a-z]+(\([^)]*\))?!:", subject))

        if "breaking change" in subject or "breaking change" in body or conventional_breaking:
            categories["breaking_changes"].append(commit)
        elif prefix.startswith("security"):
            categories["security"].append(commit)
        elif prefix.startswith(("feat", "feature")):
            categories["features"].append(commit)
        elif prefix.startswith(("fix", "bugfix", "bug", "hotfix")):
            categories["fixes"].append(commit)
        elif prefix.startswith("refactor"):
            categories["refactorings"].append(commit)
        elif prefix.startswith(("doc", "docs")):
            categories["docs"].append(commit)
        elif prefix.startswith(("test", "tests")):
            categories["tests"].append(commit)
        elif prefix.startswith(("chore", "ci", "build", "style", "perf")):
            categories["other"].append(commit)
        else:
            files = commit["files"]
            if files and all(_looks_like_docs(f) for f in files):
                categories["docs"].append(commit)
            elif files and all(_looks_like_test(f) for f in files):
                categories["tests"].append(commit)
            else:
                categories["other"].append(commit)

    return categories


# --------------------------------------------------------------------------- #
# Prompt building (with an overall size budget)
# --------------------------------------------------------------------------- #
def _truncate_diff(diff):
    lines = diff.split("\n")
    if len(lines) > PER_COMMIT_DIFF_LINE_LIMIT:
        head = "\n".join(lines[:PER_COMMIT_DIFF_LINE_LIMIT])
        return f"{head}\n... (diff truncated, showing first {PER_COMMIT_DIFF_LINE_LIMIT} of {len(lines)} lines)"
    return diff


def _render_commit(commit, include_diff):
    parts = [f"### Commit {commit['hash'][:8]} — {commit['subject']}"]
    if commit["body"]:
        parts.append(f"Body:\n{commit['body']}")
    for pr in commit["pr_bodies"]:
        parts.append(f"Referenced PR:\n{pr}")
    if commit["files"]:
        parts.append("Files changed:\n" + "\n".join(f"- {f}" for f in commit["files"]))
    if include_diff:
        parts.append(f"Diff:\n```diff\n{_truncate_diff(commit['diff'])}\n```")
    return "\n".join(parts) + "\n"


def _render_context(categorized, include_diff):
    out = ["# Commit information for changelog generation\n"]
    for key, label in CATEGORY_ORDER:
        commits = categorized[key]
        if not commits:
            continue
        out.append(f"## Pre-categorized as: {label}\n")
        for commit in commits:
            out.append(_render_commit(commit, include_diff))
    return "\n".join(out)


def build_context(categorized, budget=PROMPT_CHAR_BUDGET, allow_diffs=True):
    """Build the commit-context block, degrading to stay within `budget` chars."""
    if allow_diffs:
        context = _render_context(categorized, include_diff=True)
        if len(context) <= budget:
            return context
        print(
            f"Commit context {len(context)} chars exceeds budget {budget}; "
            "dropping diff bodies to fit."
        )
    context = _render_context(categorized, include_diff=False)
    if len(context) > budget:
        print(f"Commit context still over budget; truncating to {budget} chars.")
        context = context[:budget] + "\n... (commit context truncated to fit budget)"
    return context


def read_exemplar(changelog_file=CHANGELOG_FILE):
    """Return the most recent existing changelog section body as a style exemplar."""
    if not os.path.exists(changelog_file):
        return ""
    with open(changelog_file, "r", encoding="utf-8") as f:
        content = f.read()
    _, sections = split_changelog(content)
    for section in sections:
        if section["version"].lower() == "unreleased":
            continue
        body = section["body"].strip()
        if len(body) > EXEMPLAR_CHAR_LIMIT:
            body = body[:EXEMPLAR_CHAR_LIMIT] + "\n... (exemplar truncated)"
        return body
    return ""


def build_prompt(context, exemplar, human_draft):
    exemplar_block = ""
    if exemplar:
        exemplar_block = (
            "\nSTYLE EXEMPLAR — a previous hand-written entry. Match its voice, "
            "structure, altitude, and level of specificity (do NOT copy its content):\n"
            f"<exemplar>\n{exemplar}\n</exemplar>\n"
        )
    draft_block = ""
    if human_draft:
        draft_block = (
            "\nHUMAN DRAFT — authoritative notes already written for this release. "
            "Complete and refine these; do NOT discard or contradict them:\n"
            f"<draft>\n{human_draft}\n</draft>\n"
        )

    return f"""You are writing the CHANGELOG entry for a new release of Spring User Framework.

REPOSITORY CONTEXT
- This is a reusable Spring Boot *library*, not an application. Its consumers are other
  Spring Boot apps.
- Consumers configure it via properties under the `user.*` prefix.
- Its REST endpoints return a numeric response `code` in the JSON body; describe HTTP
  status changes and response `code` changes explicitly.
- Frame every change in terms of its impact on a *consuming application*.

INPUT
Below is the commit information for this release (messages, bodies, referenced PR
descriptions, file lists, and where available code diffs), pre-categorized by a
heuristic. Trust the actual changes over the heuristic categories.
{exemplar_block}{draft_block}
{context}

OUTPUT REQUIREMENTS
- Output GitHub-flavored Markdown for the body of ONE release section. Do NOT include
  the `## [version]` heading or the date — those are added automatically. Start with a
  one- to two-sentence summary paragraph of the release.
- After the summary, add a **SemVer classification** line: state whether this is a
  major, minor, or patch release and give a one-sentence rationale (new public API →
  minor; incompatible API change → major; otherwise patch).
- Then use these `###` subsections, in this order, OMITTING any that are empty:
  `### Security`, `### Breaking Changes`, `### Features`, `### Fixes`,
  `### Refactoring`, `### Documentation`, `### Testing`, `### Other Changes`.
- Lead with `### Security` whenever there are security-relevant changes. Reference any
  CWE identifiers and ticket IDs (e.g. `SUF-01`) found in the commit text.
- If any change alters HTTP status codes, response `code` values, defaults, or requires
  action from consumers, add a `### Behavior changes (client impact)` subsection (place
  it right after the security/breaking sections) spelling out the required consumer action.
- Use the EXACT class, property, and endpoint names from the diffs and commit text. Do
  not invent names. Prefer specifics (status codes, property keys) over generalities.
- Ignore release-plumbing noise; every bullet must describe a real code or doc change.
"""


# --------------------------------------------------------------------------- #
# AI + deterministic generation
# --------------------------------------------------------------------------- #
def _create_completion(client, model, messages, temperature):
    """Call chat.completions, retrying without temperature if the model rejects it."""
    try:
        kwargs = {"model": model, "messages": messages}
        if temperature is not None:
            kwargs["temperature"] = temperature
        return client.chat.completions.create(**kwargs)
    except Exception as exc:  # noqa: BLE001 - narrow retry on temperature rejection
        if temperature is not None and "temperature" in str(exc).lower():
            print(f"Model rejected temperature={temperature}; retrying with model default.")
            return client.chat.completions.create(model=model, messages=messages)
        raise


def generate_ai_changelog(prompt):
    """Call OpenAI to write the changelog body. Raises on any failure."""
    from openai import OpenAI  # lazy: keeps the offline path import-free

    token = os.environ.get("OPENAI_API_TOKEN")
    if not token:
        raise RuntimeError("OPENAI_API_TOKEN is not set")

    try:
        temperature = float(DEFAULT_TEMPERATURE)
    except ValueError:
        temperature = None

    client = OpenAI(api_key=token)
    response = _create_completion(
        client,
        DEFAULT_MODEL,
        [
            {
                "role": "system",
                "content": "You are an expert release engineer who writes precise, "
                "consumer-focused changelogs for a Spring Boot library.",
            },
            {"role": "user", "content": prompt},
        ],
        temperature,
    )
    text = (response.choices[0].message.content or "").strip()
    if not text:
        raise RuntimeError("Model returned an empty changelog")
    return _strip_code_fences(text)


def _strip_code_fences(text):
    """Remove a wrapping ```markdown ... ``` fence if the model added one."""
    stripped = text.strip()
    if stripped.startswith("```"):
        lines = stripped.split("\n")
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        return "\n".join(lines).strip()
    return text


def generate_fallback_changelog(categorized, human_draft):
    """Deterministic, offline changelog body from categorized commits."""
    lines = [
        "_This section was generated offline from commit metadata "
        "(AI generation was unavailable); review and refine before publishing._",
    ]
    if human_draft:
        lines.append("")
        lines.append(human_draft.strip())

    for key, label in CATEGORY_ORDER:
        commits = [c for c in categorized[key] if not MERGE_COMMIT_RE.match(c["subject"])]
        if not commits:
            continue
        lines.append("")
        lines.append(f"### {label}")
        for commit in commits:
            subject = commit["subject"].strip()
            pr = f" (#{commit['pr_numbers'][0]})" if commit["pr_numbers"] else ""
            lines.append(f"- {subject}{pr} ({commit['hash'][:8]})")
    return "\n".join(lines).strip()


# --------------------------------------------------------------------------- #
# CHANGELOG.md read/split/write
# --------------------------------------------------------------------------- #
def split_changelog(content):
    """Split CHANGELOG content into (head, sections).

    `head` is the title/preamble before the first `## [..]` heading. Each section is
    a dict with `version`, `heading`, `body`, and `raw`.
    """
    lines = content.splitlines(keepends=True)
    first_idx = next(
        (i for i, ln in enumerate(lines) if VERSION_HEADING_RE.match(ln)), None
    )
    if first_idx is None:
        return content, []

    head = "".join(lines[:first_idx])
    sections = []
    current = None
    for ln in lines[first_idx:]:
        m = VERSION_HEADING_RE.match(ln)
        if m:
            if current:
                sections.append(current)
            current = {"version": m.group(1), "heading": ln, "body": "", "raw": ln}
        else:
            current["body"] += ln
            current["raw"] += ln
    if current:
        sections.append(current)
    return head, sections


EMPTY_UNRELEASED = "## [Unreleased]\n\n"


def compose_changelog(head, sections, version, today, new_body):
    """Assemble a full CHANGELOG, inserting the new version section below the title.

    - Consumes and resets any `## [Unreleased]` section (its notes were folded in).
    - Replaces any pre-existing section for the same `version` (dedup / refine).
    - Keeps `[Unreleased]` at the top when present.
    """
    had_unreleased = any(s["version"].lower() == "unreleased" for s in sections)
    kept = [
        s
        for s in sections
        if s["version"].lower() != "unreleased" and s["version"] != version
    ]

    new_section = f"## [{version}] - {today}\n\n{new_body.strip()}\n\n"

    if head and not head.endswith("\n\n"):
        head = head.rstrip("\n") + "\n\n"
    parts = [head]
    if had_unreleased:
        parts.append(EMPTY_UNRELEASED)
    parts.append(new_section)
    for s in kept:
        parts.append(s["raw"])
    return "".join(parts)


def extract_human_draft(sections, version):
    """Gather human-written notes to feed the model: Unreleased + same-version body."""
    drafts = []
    for s in sections:
        if s["version"].lower() == "unreleased" and s["body"].strip():
            drafts.append(s["body"].strip())
        elif s["version"] == version and s["body"].strip():
            drafts.append(s["body"].strip())
    return "\n\n".join(drafts).strip()


DEFAULT_HEADER = (
    "# Changelog\n\n"
    "All notable changes to this project are documented here. "
    "This project follows [Semantic Versioning](https://semver.org/).\n\n"
)


def update_changelog(version, new_body, changelog_file=CHANGELOG_FILE):
    today = date.today().strftime("%Y-%m-%d")
    if os.path.exists(changelog_file):
        with open(changelog_file, "r", encoding="utf-8") as f:
            content = f.read()
        head, sections = split_changelog(content)
        if not head.strip():
            head = DEFAULT_HEADER
    else:
        head, sections = DEFAULT_HEADER, []

    result = compose_changelog(head, sections, version, today, new_body)
    with open(changelog_file, "w", encoding="utf-8") as f:
        f.write(result)


# --------------------------------------------------------------------------- #
# Entry point
# --------------------------------------------------------------------------- #
def resolve_version(cli_version):
    if cli_version:
        return cli_version
    if sys.stdin.isatty():
        return input("Enter the new version (e.g., 1.0.0): ").strip()
    print(
        "ERROR: no version argument provided and no interactive terminal available. "
        "Pass the version as the first argument (e.g. `generate_changelog.py 1.2.3`).",
        file=sys.stderr,
    )
    sys.exit(2)


def build_changelog_body(commits, version):
    """Produce the changelog body, using AI when possible and falling back cleanly."""
    categorized = categorize_commits(commits)

    human_draft = ""
    if os.path.exists(CHANGELOG_FILE):
        with open(CHANGELOG_FILE, "r", encoding="utf-8") as f:
            _, sections = split_changelog(f.read())
        human_draft = extract_human_draft(sections, version)

    if os.environ.get("CHANGELOG_SKIP_AI") or not os.environ.get("OPENAI_API_TOKEN"):
        reason = "CHANGELOG_SKIP_AI set" if os.environ.get("CHANGELOG_SKIP_AI") else "no OPENAI_API_TOKEN"
        print(f"AI changelog disabled ({reason}); using deterministic generator.")
        return generate_fallback_changelog(categorized, human_draft)

    # PR descriptions only feed the AI context, so fetch them only on the AI path.
    fetch_pr_bodies(commits)
    allow_diffs = not os.environ.get("CHANGELOG_NO_DIFFS")
    context = build_context(categorized, allow_diffs=allow_diffs)
    exemplar = read_exemplar()
    prompt = build_prompt(context, exemplar, human_draft)
    try:
        print("Generating changelog via OpenAI...")
        return generate_ai_changelog(prompt)
    except Exception as exc:  # noqa: BLE001 - never let this abort a release
        print(
            f"WARNING: AI changelog generation failed ({exc}); "
            "falling back to the deterministic generator.",
            file=sys.stderr,
        )
        return generate_fallback_changelog(categorized, human_draft)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Generate a CHANGELOG.md release entry.")
    parser.add_argument("version", nargs="?", help="Release version, e.g. 1.2.3")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the generated section without writing CHANGELOG.md.",
    )
    args = parser.parse_args(argv)

    try:
        last_ref = get_last_reference()
        commits = get_commits(last_ref)
    except Exception as exc:  # noqa: BLE001 - collection failure must not abort a release
        print(
            f"WARNING: could not collect git history for the changelog ({exc}); "
            "skipping changelog update.",
            file=sys.stderr,
        )
        return 0

    if not commits:
        print("No new commits found since", last_ref)
        return 0

    print(f"Found {len(commits)} commits since {last_ref}")
    version = resolve_version(args.version)
    body = build_changelog_body(commits, version)

    if args.dry_run:
        today = date.today().strftime("%Y-%m-%d")
        print("\n--- DRY RUN (CHANGELOG.md not modified) ---\n")
        print(f"## [{version}] - {today}\n\n{body}\n")
        return 0

    update_changelog(version, body)
    print(f"Changelog updated for version {version}!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
