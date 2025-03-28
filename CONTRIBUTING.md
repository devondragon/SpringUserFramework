# Contributing to SpringUserFramework

Thank you for considering contributing to **SpringUserFramework**, a Spring Boot library designed to simplify user management and authentication. Whether you're fixing bugs, building new features, or improving documentation, your contributions are welcome and appreciated.

This guide covers how to set up your local environment, contribute to both the library and the companion demo app, and submit high-quality pull requests.

---

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Forking and Cloning](#forking-and-cloning)
- [Working on Issues](#working-on-issues)
- [Branching and Development Workflow](#branching-and-development-workflow)
- [Testing Your Changes](#testing-your-changes)
- [Writing Unit Tests](#writing-unit-tests)
- [Code Style and Standards](#code-style-and-standards)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Providing Easy Integration Steps](#providing-easy-integration-steps)
- [Community Support](#community-support)

---

## Code of Conduct
We expect all contributors to follow our [Code of Conduct](CODE_OF_CONDUCT.md). Be respectful and inclusive.

---

## Prerequisites
Before starting development, make sure you have the following installed:

- JDK 17 or later
- Gradle
- Maven
- Git
- Docker (optional for demo app DB)
- IntelliJ IDEA or VSCode (recommended — the maintainer uses VSCode)

---

## Project Structure
- **SpringUserFramework** – The main Spring Boot library.
- **SpringUserFrameworkDemoApp** – A companion frontend/demo application used to test and showcase library features. [GitHub Link](https://github.com/devondragon/SpringUserFrameworkDemoApp)

---

## Forking and Cloning
1. **Fork both repositories:**
   - [SpringUserFramework](https://github.com/devondragon/SpringUserFramework)
   - [SpringUserFrameworkDemoApp](https://github.com/devondragon/SpringUserFrameworkDemoApp)

2. **Clone locally:**
```bash
git clone https://github.com/your-username/SpringUserFramework.git
git clone https://github.com/your-username/SpringUserFrameworkDemoApp.git
```

3. **Add upstream remotes:**
```bash
cd SpringUserFramework
git remote add upstream https://github.com/devondragon/SpringUserFramework.git

cd ../SpringUserFrameworkDemoApp
git remote add upstream https://github.com/devondragon/SpringUserFrameworkDemoApp.git
```

---

## Working on Issues
1. Check the [Issues](https://github.com/devondragon/SpringUserFramework/issues) tab.
2. Comment to claim an issue.
3. Sync your fork and create a new branch off the target branch:
```bash
git fetch upstream
git checkout -b feature/my-change upstream/branch-name
```

---

## Branching and Development Workflow
1. Work on your feature or fix in a branch.
2. Make clear, well-scoped commits. Use [Conventional Commits](https://www.conventionalcommits.org/).
3. Push to your fork:
```bash
git push origin feature/my-change
```

> **Note:** Please do not commit or push changes to the version number in `gradle.properties` or any release metadata. Version management is handled by the project maintainer.

---

## Testing Your Changes
Since this is a library, you’ll want to test changes via the **Demo App**:

### Steps:
1. **Build and publish the library locally:**
```bash
cd SpringUserFramework
./gradlew publishToMavenLocal
```

2. **Update the dependency in the demo app:**
```groovy
// In SpringUserFrameworkDemoApp/build.gradle
dependencies {
    implementation 'com.yourgroupid:spring-user-framework:1.2.3-SNAPSHOT'
}
```

3. **Run the demo app:**
```bash
cd ../SpringUserFrameworkDemoApp
./gradlew bootRun
```

### Optional: Setup DB with Docker
```bash
cd SpringUserFrameworkDemoApp
docker-compose up -d
```

---

## Writing Unit Tests
All PRs must include appropriate tests:
- New features must include unit tests.
- Bug fixes should include regression tests.
- Modified code should have updated tests.

### Run all tests:
```bash
./gradlew test
```

PRs without passing tests will not be accepted.

---

## Code Style and Standards
- Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
- Use meaningful variable/method names.
- Keep commits focused and easy to understand.

---

## Submitting a Pull Request
1. Push your feature branch to your fork.
2. Open a PR against the appropriate feature or development branch in the original repo.
3. Include:
   - A clear description
   - Related issue number(s)
   - Reference to the related DemoApp PR (if applicable)
4. Respond to feedback and revise as needed.

---

## Providing Easy Integration Steps
If you're adding support for an integration, third-party tool, or external service:

- Please ensure your PR includes **clear, step-by-step instructions** on how to set up and test the new integration.
- These steps should be easy to follow for both the maintainer and new users trying the feature.
- Add any required configuration to the README or relevant config files.

The goal is to make it simple to verify and adopt your contribution without digging through code or documentation.

---

## Community Support
If you need help while contributing:

- Open an [Issue](https://github.com/devondragon/SpringUserFramework/issues) or a [Discussion](https://github.com/devondragon/SpringUserFramework/discussions).
- Tag **@devondragon** in the comments if you’re stuck or need guidance.
- We’re happy to help clarify workflows or provide feedback on early ideas.

Thank you for helping us make this project better!

---
