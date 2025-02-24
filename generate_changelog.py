import os
import sys
import subprocess
from openai import OpenAI
from datetime import date
import re
# tempfile no longer needed

def get_git_commits_with_diffs():
    # Get the last tag
    try:
        last_tag = subprocess.check_output(
            ["git", "describe", "--tags", "--abbrev=0"], text=True
        ).strip()
    except subprocess.CalledProcessError:
        # If no tags exist, use the first commit
        last_tag = subprocess.check_output(
            ["git", "rev-list", "--max-parents=0", "HEAD"], text=True
        ).strip()
        print("No tags found. Using the first commit as reference.")

    # Get commit hashes and messages since the last tag
    commit_info = subprocess.check_output(
        ["git", "log", f"{last_tag}..HEAD", "--pretty=format:%H|%s"], text=True
    ).strip()

    if not commit_info:
        return last_tag, []

    commits_with_diffs = []
    for line in commit_info.split("\n"):
        if not line:
            continue

        commit_hash, commit_message = line.split("|", 1)

        # Get the diff for this commit
        diff = subprocess.check_output(
            ["git", "show", "--stat", "--patch", commit_hash], text=True
        )

        # Extract file changes
        files_changed = subprocess.check_output(
            ["git", "show", "--name-status", commit_hash], text=True
        ).strip()

        commits_with_diffs.append({
            "hash": commit_hash,
            "message": commit_message,
            "diff": diff,
            "files_changed": files_changed
        })

    return last_tag, commits_with_diffs

def categorize_commits(commits_with_diffs):
    """Pre-categorize commits based on conventional commit prefixes"""
    categories = {
        "features": [],
        "fixes": [],
        "breaking_changes": [],
        "refactorings": [],
        "docs": [],
        "tests": [],
        "other": []
    }

    for commit in commits_with_diffs:
        message = commit["message"].lower()

        # Check for breaking changes first
        if "breaking change" in message or "!" in message.split(":", 1)[0]:
            categories["breaking_changes"].append(commit)
        # Then check for common prefixes
        elif message.startswith(("feat", "feature")):
            categories["features"].append(commit)
        elif message.startswith(("fix", "bugfix", "bug")):
            categories["fixes"].append(commit)
        elif message.startswith("refactor"):
            categories["refactorings"].append(commit)
        elif message.startswith(("doc", "docs")):
            categories["docs"].append(commit)
        elif message.startswith(("test", "tests")):
            categories["tests"].append(commit)
        else:
            # Attempt to categorize based on the diff
            if any(term in commit["diff"].lower() for term in ["fix", "bug", "issue", "error", "crash"]):
                categories["fixes"].append(commit)
            elif any(term in commit["diff"].lower() for term in ["feat", "feature", "add", "new", "implement"]):
                categories["features"].append(commit)
            else:
                categories["other"].append(commit)

    return categories

def generate_changelog(commits_with_diffs, categorized_commits):
    if not commits_with_diffs:
        return "No commits to include in the changelog."

    # Prepare the detailed diff information
    diff_content = "# Git Commit Information for Changelog Generation\n\n"

    # Add categorized commit information
    for category, commits in categorized_commits.items():
        if commits:
            diff_content += f"## {category.replace('_', ' ').title()}\n"
            for commit in commits:
                diff_content += f"### Commit: {commit['hash'][:8]} - {commit['message']}\n"

                # Add file changes summary
                diff_content += "#### Files Changed:\n"
                diff_content += f"```\n{commit['files_changed']}\n```\n"

                # Add a truncated diff only if it's very long (over 500 lines)
                diff_lines = commit['diff'].split('\n')
                if len(diff_lines) > 500:
                    truncated_diff = '\n'.join(diff_lines[:500])
                    truncated_diff += f"\n... (diff truncated, showing first 500 of {len(diff_lines)} lines)"
                else:
                    truncated_diff = commit['diff']

                diff_content += "#### Diff Preview:\n"
                diff_content += f"```diff\n{truncated_diff}\n```\n\n"

    # Build the prompt with categorized information and diff data
    prompt = f"""
You are a skilled software developer tasked with creating a detailed changelog.
I have provided you with git commit information including:
1. Commit messages
2. File changes
3. Code diffs

Please generate a clear, comprehensive changelog based on this information.
The commits have been pre-categorized, but please use the actual code changes to:
- Improve category assignments if needed
- Add more specific details about what changed in each commit
- Extract key implementation details from the diffs
- Identify significant changes that aren't clear from just the commit messages

Here is the detailed commit information:

{diff_content}

Format the changelog in Markdown as follows:
### Features
- Detailed feature descriptions here, with substantive information from the diffs

### Fixes
- Detailed bug fix descriptions here, with substantive information from the diffs

### Breaking Changes
- Detailed descriptions of breaking changes here (if any), with clear explanations of what changed

### Refactoring
- Important code refactoring changes (if any)

### Documentation
- Documentation updates (if any)

### Testing
- Test-related changes (if any)

### Other Changes
- Any other significant changes

Important: Focus on providing value to humans reading the changelog. Explain changes in user-centric terms where possible.
"""

    client = OpenAI(
        api_key=os.environ.get("OPENAI_API_TOKEN"),
    )

    # Use GPT-4o (without fallback)
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": "You are a helpful assistant for software development with expertise in analyzing code changes and creating detailed changelogs."},
            {"role": "user", "content": prompt},
        ],
    )
    changelog = response.choices[0].message.content.strip()

    # No temporary file to clean up anymore

    return changelog

def update_changelog(version, changelog_content):
    changelog_file = "CHANGELOG.md"
    today = date.today().strftime("%Y-%m-%d")
    new_entry = f"## [{version}] - {today}\n{changelog_content}\n\n"

    if os.path.exists(changelog_file):
        with open(changelog_file, "r+") as f:
            old_content = f.read()
            f.seek(0, 0)
            f.write(new_entry + old_content)
    else:
        with open(changelog_file, "w") as f:
            f.write(new_entry)

if __name__ == "__main__":
    last_tag, commits_with_diffs = get_git_commits_with_diffs()

    if not commits_with_diffs:
        print("No new commits found.")
        sys.exit(0)

    print(f"Found {len(commits_with_diffs)} commits since {last_tag}")

    # Pre-categorize commits for better LLM results
    categorized_commits = categorize_commits(commits_with_diffs)

    print("Generating detailed changelog...")
    changelog_content = generate_changelog(commits_with_diffs, categorized_commits)

    print("\nGenerated Changelog:")
    print(changelog_content)

    # Check if a version was passed as a command-line argument
    if len(sys.argv) > 1:
        new_version = sys.argv[1]
    else:
        # Prompt for a version if none was provided
        new_version = input("Enter the new version (e.g., 1.0.0): ").strip()

    update_changelog(new_version, changelog_content)
    print(f"Changelog updated for version {new_version}!")
