import os
import sys
import subprocess
from openai import OpenAI
from datetime import date

def get_git_commits():
    # Get the last tag
    last_tag = subprocess.check_output(
        ["git", "describe", "--tags", "--abbrev=0"], text=True
    ).strip()

    # Get commit messages since the last tag
    commits = subprocess.check_output(
        ["git", "log", f"{last_tag}..HEAD", "--pretty=format:%s"], text=True
    ).strip()

    return last_tag, commits.split("\n")

def generate_changelog(commits):
    if not commits:
        return "No commits to include in the changelog."

    prompt = f"""
    You are a helpful assistant tasked with creating a changelog. Based on these Git commit messages, generate a clear, human-readable changelog:

    Commit messages:
    {commits}

    Format the changelog as follows:
    ### Features
    - List features here

    ### Fixes
    - List fixes here

    ### Breaking Changes
    - List breaking changes here (if any)
    """

    client = OpenAI(
        api_key=os.environ.get("OPENAI_API_TOKEN"),  # This is the default and can be omitted
    )
    response = client.chat.completions.create(
        model="gpt-4",
        messages=[
            {"role": "system", "content": "You are a helpful assistant for software development."},
            {"role": "user", "content": prompt},
        ],
    )
    return response.choices[0].message.content.strip()

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
    last_tag, commits = get_git_commits()
    if not commits:
        print("No new commits found.")
        exit()

    print("Generating changelog...")
    changelog_content = generate_changelog("\n".join(commits))

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
