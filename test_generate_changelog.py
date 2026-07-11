"""Tests for generate_changelog.py pure functions.

Run with:  python -m unittest test_generate_changelog
No network or OpenAI package required (the OpenAI import is lazy).
"""

import os
import tempfile
import unittest
from datetime import date
from unittest import mock

import generate_changelog as gc


def _commit(subject, body="", files=None, diff="", pr_numbers=None):
    return {
        "hash": "abcdef1234567890",
        "subject": subject,
        "body": body,
        "diff": diff,
        "files": files or [],
        "pr_numbers": pr_numbers or [],
        "pr_bodies": [],
    }


class ReleasePlumbingTests(unittest.TestCase):
    def test_matches_gradle_release_plugin_commits(self):
        for msg in (
            "[Gradle Release Plugin] - new version commit: '5.1.1-SNAPSHOT'.",
            "[Gradle Release Plugin] - pre tag commit: '5.1.0'.",
        ):
            self.assertIsNotNone(gc.RELEASE_PLUMBING_RE.search(msg), msg)

    def test_ignores_real_commits(self):
        self.assertIsNone(gc.RELEASE_PLUMBING_RE.search("feat: add StepUpService SPI"))

    def test_does_not_drop_legit_commit_mentioning_snapshot(self):
        # A bare "-SNAPSHOT" mention must not be treated as release plumbing.
        self.assertIsNone(
            gc.RELEASE_PLUMBING_RE.search("chore: verify build against 6.0.0-SNAPSHOT")
        )


class ParseStatFilesTests(unittest.TestCase):
    def test_parses_paths_and_renames(self):
        show = (
            "commit abc\n\n    subject\n\n"
            " src/main/java/Foo.java | 12 ++++----\n"
            " docs/{old => new}/Guide.md | 4 +--\n"
            " bin/logo.png | Bin 0 -> 10 bytes\n"
            " 3 files changed\n\n"
            "diff --git a/src/main/java/Foo.java b/src/main/java/Foo.java\n"
        )
        files = gc._parse_stat_files(show)
        self.assertIn("src/main/java/Foo.java", files)
        self.assertIn("bin/logo.png", files)
        self.assertTrue(any(f.endswith("Guide.md") for f in files))
        self.assertNotIn("3 files changed", " ".join(files))


class CategorizeTests(unittest.TestCase):
    def test_conventional_prefixes(self):
        commits = [
            _commit("feat: new thing"),
            _commit("fix: a bug"),
            _commit("docs: update readme"),
            _commit("refactor: tidy"),
            _commit("test: add coverage"),
            _commit("security: patch CWE-640"),
            _commit("feat!: breaking api"),
            _commit("chore: bump dep"),
        ]
        cats = gc.categorize_commits(commits)
        self.assertEqual(len(cats["features"]), 1)
        self.assertEqual(len(cats["fixes"]), 1)
        self.assertEqual(len(cats["docs"]), 1)
        self.assertEqual(len(cats["refactorings"]), 1)
        self.assertEqual(len(cats["tests"]), 1)
        self.assertEqual(len(cats["security"]), 1)
        self.assertEqual(len(cats["breaking_changes"]), 1)
        self.assertEqual(len(cats["other"]), 1)

    def test_breaking_change_in_body(self):
        cats = gc.categorize_commits([_commit("update api", body="BREAKING CHANGE: removed X")])
        self.assertEqual(len(cats["breaking_changes"]), 1)

    def test_trailing_bang_is_not_breaking(self):
        # A non-conventional subject that merely ends in "!" must not be breaking.
        cats = gc.categorize_commits([_commit("fix the thing already!")])
        self.assertEqual(len(cats["breaking_changes"]), 0)
        self.assertEqual(len(cats["fixes"]), 1)

    def test_path_based_fallback_docs_and_tests(self):
        cats = gc.categorize_commits(
            [
                _commit("update stuff", files=["README.md", "docs/Guide.md"]),
                _commit("more stuff", files=["src/test/java/FooTest.java"]),
                _commit("mixed", files=["src/main/java/Foo.java", "README.md"]),
            ]
        )
        self.assertEqual(len(cats["docs"]), 1)
        self.assertEqual(len(cats["tests"]), 1)
        self.assertEqual(len(cats["other"]), 1)

    def test_no_diff_substring_guessing(self):
        # A non-conventional commit whose diff merely contains the word "fix"
        # must NOT be bucketed as a fix (the old heuristic did this).
        cats = gc.categorize_commits(
            [_commit("adjust config", files=["src/main/java/Foo.java"], diff="+ // fix typo")]
        )
        self.assertEqual(len(cats["fixes"]), 0)
        self.assertEqual(len(cats["other"]), 1)


class SplitAndComposeTests(unittest.TestCase):
    SAMPLE = (
        "# Changelog\n\n"
        "Preamble text.\n\n"
        "## [5.0.1] - 2026-06-15\n"
        "### Fixes\n- old fix\n\n"
        "## [5.0.0] - 2026-05-01\n"
        "### Features\n- old feature\n\n"
    )

    def test_split_head_and_sections(self):
        head, sections = gc.split_changelog(self.SAMPLE)
        self.assertIn("# Changelog", head)
        self.assertIn("Preamble", head)
        self.assertEqual([s["version"] for s in sections], ["5.0.1", "5.0.0"])

    def test_compose_inserts_below_title(self):
        head, sections = gc.split_changelog(self.SAMPLE)
        result = gc.compose_changelog(head, sections, "5.1.0", "2026-07-11", "### Security\n- new")
        # Title comes first, new version sits below it and above the previous latest.
        self.assertLess(result.index("# Changelog"), result.index("## [5.1.0]"))
        self.assertLess(result.index("## [5.1.0]"), result.index("## [5.0.1]"))
        self.assertIn("### Security", result)

    def test_compose_dedupes_same_version(self):
        # A hand-written 5.1.0 section already exists; composing a fresh 5.1.0 replaces it.
        head, sections = gc.split_changelog(
            "# Changelog\n\n## [5.1.0] - 2026-07-01\n### Features\n- hand draft\n\n"
            "## [5.0.1] - 2026-06-15\n### Fixes\n- old\n\n"
        )
        result = gc.compose_changelog(head, sections, "5.1.0", "2026-07-11", "### Security\n- refined")
        self.assertEqual(result.count("## [5.1.0]"), 1)
        self.assertIn("### Security", result)
        self.assertNotIn("hand draft", result)

    def test_compose_resets_unreleased(self):
        content = (
            "# Changelog\n\n"
            "## [Unreleased]\n### Features\n- jotted note\n\n"
            "## [5.0.1] - 2026-06-15\n### Fixes\n- old\n\n"
        )
        head, sections = gc.split_changelog(content)
        result = gc.compose_changelog(head, sections, "5.1.0", "2026-07-11", "### Features\n- done")
        self.assertEqual(result.count("## [Unreleased]"), 1)
        self.assertNotIn("jotted note", result)
        # Unreleased stays on top, new version below it.
        self.assertLess(result.index("## [Unreleased]"), result.index("## [5.1.0]"))


class HumanDraftTests(unittest.TestCase):
    def test_extracts_unreleased_and_same_version(self):
        content = (
            "# Changelog\n\n"
            "## [Unreleased]\n- unreleased note\n\n"
            "## [5.1.0] - 2026-07-01\n- version draft\n\n"
        )
        _, sections = gc.split_changelog(content)
        draft = gc.extract_human_draft(sections, "5.1.0")
        self.assertIn("unreleased note", draft)
        self.assertIn("version draft", draft)

    def test_none_version_still_gets_unreleased(self):
        content = "# Changelog\n\n## [Unreleased]\n- note\n\n"
        _, sections = gc.split_changelog(content)
        draft = gc.extract_human_draft(sections, None)
        self.assertIn("note", draft)


class UpdateChangelogTests(unittest.TestCase):
    def test_creates_file_when_missing(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "CHANGELOG.md")
            gc.update_changelog("1.0.0", "### Features\n- first", changelog_file=path)
            with open(path, encoding="utf-8") as f:
                content = f.read()
            self.assertIn("# Changelog", content)
            self.assertIn("## [1.0.0]", content)
            today = date.today().strftime("%Y-%m-%d")
            self.assertIn(today, content)

    def test_inserts_below_existing_title(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "CHANGELOG.md")
            with open(path, "w", encoding="utf-8") as f:
                f.write("# Changelog\n\nPreamble.\n\n## [1.0.0] - 2026-01-01\n- old\n\n")
            gc.update_changelog("1.1.0", "### Features\n- new", changelog_file=path)
            with open(path, encoding="utf-8") as f:
                content = f.read()
            self.assertLess(content.index("# Changelog"), content.index("## [1.1.0]"))
            self.assertLess(content.index("## [1.1.0]"), content.index("## [1.0.0]"))


class BudgetTests(unittest.TestCase):
    def test_drops_diffs_when_over_budget(self):
        big_diff = "diff --git a/x b/x\n" + ("+line\n" * 2000)
        cats = gc.categorize_commits([_commit("feat: big", files=["x"], diff=big_diff)])
        context = gc.build_context(cats, budget=500, allow_diffs=True)
        self.assertLessEqual(len(context), 500 + len("\n... (commit context truncated to fit budget)"))
        self.assertNotIn("+line", context)


class MiscTests(unittest.TestCase):
    def test_strip_code_fences(self):
        self.assertEqual(gc._strip_code_fences("```markdown\n### X\n- y\n```"), "### X\n- y")
        self.assertEqual(gc._strip_code_fences("### X\n- y"), "### X\n- y")

    def test_fallback_changelog_lists_commits(self):
        cats = gc.categorize_commits(
            [_commit("feat: add thing", pr_numbers=[42]), _commit("fix: a bug")]
        )
        body = gc.generate_fallback_changelog(cats, human_draft="")
        self.assertIn("### Features", body)
        self.assertIn("add thing", body)
        self.assertIn("#42", body)
        self.assertIn("### Fixes", body)

    def test_fallback_includes_human_draft(self):
        cats = gc.categorize_commits([_commit("feat: x")])
        body = gc.generate_fallback_changelog(cats, human_draft="- authoritative note")
        self.assertIn("authoritative note", body)

    def test_fallback_excludes_merge_commits(self):
        cats = gc.categorize_commits(
            [
                _commit("Merge pull request #339 from devondragon/docs/cleanup", files=["CHANGELOG.md"]),
                _commit("docs: real change", files=["CHANGELOG.md"]),
            ]
        )
        body = gc.generate_fallback_changelog(cats, human_draft="")
        self.assertNotIn("Merge pull request", body)
        self.assertIn("real change", body)

    def test_read_exemplar_skips_unreleased(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "CHANGELOG.md")
            with open(path, "w", encoding="utf-8") as f:
                f.write(
                    "# Changelog\n\n## [Unreleased]\n- skip me\n\n"
                    "## [1.0.0] - 2026-01-01\n### Features\n- real exemplar\n\n"
                )
            exemplar = gc.read_exemplar(changelog_file=path)
            self.assertIn("real exemplar", exemplar)
            self.assertNotIn("skip me", exemplar)


class ReliabilityTests(unittest.TestCase):
    def test_main_returns_zero_on_collection_failure(self):
        # A git-collection failure must be non-fatal (return 0), not abort the release.
        with mock.patch.object(gc, "get_last_reference", return_value="v1"), mock.patch.object(
            gc, "get_commits", side_effect=RuntimeError("boom")
        ):
            self.assertEqual(gc.main(["9.9.9"]), 0)

    def test_skip_ai_does_not_fetch_pr_bodies(self):
        commits = [_commit("feat: x", pr_numbers=[7])]
        with mock.patch.dict(os.environ, {"CHANGELOG_SKIP_AI": "1"}, clear=False), mock.patch.object(
            gc, "fetch_pr_bodies"
        ) as fetch:
            gc.build_changelog_body(commits, "1.0.0")
            fetch.assert_not_called()

    def test_ai_failure_falls_back_to_deterministic(self):
        # The headline contract: an AI-path failure degrades to the offline generator.
        commits = [_commit("feat: something")]
        env = {"CHANGELOG_SKIP_AI": "", "OPENAI_API_TOKEN": "test-token"}
        with mock.patch.dict(os.environ, env, clear=False), mock.patch.object(
            gc, "fetch_pr_bodies"
        ), mock.patch.object(gc, "generate_ai_changelog", side_effect=RuntimeError("api down")):
            body = gc.build_changelog_body(commits, "1.0.0")
        self.assertIn("generated offline", body)
        self.assertIn("### Features", body)
        self.assertIn("something", body)

    def test_no_tags_uses_full_history_range(self):
        # With no tags (last_ref is None), the git log range must be HEAD (includes root).
        calls = []

        def fake_git(args):
            calls.append(args)
            return ""  # empty log -> no commits, short-circuits

        with mock.patch.object(gc, "_git", side_effect=fake_git):
            gc.get_commits(None)
        log_args = calls[0]
        self.assertIn("HEAD", log_args)
        self.assertNotIn("..HEAD", " ".join(log_args))

    def test_pr_fetch_capped(self):
        commits = [_commit(f"feat: x{i}", pr_numbers=[i]) for i in range(5)]
        with mock.patch.dict(os.environ, {}, clear=False), mock.patch.object(
            gc, "MAX_PR_FETCHES", 2
        ), mock.patch("shutil.which", return_value="/usr/bin/gh"), mock.patch.object(
            gc, "_fetch_single_pr", return_value=None
        ) as fetch:
            gc.fetch_pr_bodies(commits)
        self.assertEqual(fetch.call_count, 2)


if __name__ == "__main__":
    unittest.main()
