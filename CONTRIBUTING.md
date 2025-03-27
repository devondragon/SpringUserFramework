# Contributing to SpringUserFramework

Thank you for considering contributing to SpringUserFramework! We appreciate your time and effort in improving our project. The following guidelines will help you navigate the contribution process.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
  - [Forking the Repository](#forking-the-repository)
  - [Cloning Your Fork](#cloning-your-fork)
  - [Setting Up the Upstream Remote](#setting-up-the-upstream-remote)
- [Working on Issues](#working-on-issues)
  - [Creating a Feature Branch](#creating-a-feature-branch)
  - [Making Changes](#making-changes)
  - [Committing Changes](#committing-changes)
  - [Pushing Changes](#pushing-changes)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Code Style and Standards](#code-style-and-standards)
- [Testing](#testing)
- [Documentation](#documentation)
- [Community Support](#community-support)

## Code of Conduct

Please note that this project is released with a [Contributor Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project, you agree to abide by its terms.

## Getting Started

### Forking the Repository

To contribute to SpringUserFramework, begin by forking the repository to your GitHub account. This creates a personal copy of the project where you can make changes without affecting the original repository.

1. Navigate to the [SpringUserFramework repository](https://github.com/devondragon/SpringUserFramework/).
2. Click the "Fork" button in the upper right corner.

### Cloning Your Fork

Next, clone your fork to your local machine to start making changes.

```bash
git clone https://github.com/your-username/SpringUserFramework.git
cd SpringUserFramework
```


### Setting Up the Upstream Remote

To keep your fork synchronized with the original repository, add it as an upstream remote:

```bash
git remote add upstream https://github.com/devondragon/SpringUserFramework.git
```


This setup allows you to fetch updates from the main repository and incorporate them into your fork.

## Working on Issues

We use GitHub Issues to track bugs and feature requests. Before starting work, please check existing issues to avoid duplication.

1. Browse the [Issues](https://github.com/devondragon/SpringUserFramework/issues) to find something you'd like to work on.
2. Comment on the issue to let others know you're working on it.

### Creating a Feature Branch

For each issue, create a new branch in your local repository. This keeps your changes organized and makes it easier to manage multiple contributions.

1. Fetch the latest changes from the upstream repository:

   ```bash
   git fetch upstream
   ```


2. Check out the branch associated with the issue:

   ```bash
   git checkout upstream/branch-name
   ```


3. Create a new branch off of the issue branch:

   ```bash
   git checkout -b your-feature-branch
   ```


### Making Changes

Make your desired changes in your local repository. Ensure that your code adheres to the project's coding standards and includes appropriate documentation.

### Committing Changes

Commit your changes with clear and descriptive messages.

```bash
git add .
git commit -m "Brief description of your changes"
```


Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages.

### Pushing Changes

Push your feature branch to your fork on GitHub:

```bash
git push origin your-feature-branch
```



## Unit Tests and Test Coverage

All contributions to `SpringUserFramework` **must include appropriate unit tests**. This ensures that the library remains stable, reliable, and maintainable as it grows.

### Requirements

- **All new features** must include unit tests that thoroughly cover the new functionality.
- **Bug fixes** should include tests that demonstrate the issue and confirm the fix.
- If you are **modifying existing code**, you should update or expand the relevant tests to reflect those changes.
- **All tests must pass** before a pull request will be considered for merging.

### Running Tests Locally

Before submitting a PR, run the full test suite to ensure your changes do not introduce regressions:

```bash
./gradlew test
```

If you are using an IDE like VSCode or IntelliJ, you can also run the tests from the IDE directly.

We may add code coverage reporting tools in the future to help enforce this, but for now, maintainers will review tests as part of the code review process.


## Submitting a Pull Request

Once your changes are pushed to your fork, submit a pull request (PR) to the original repository:

1. Navigate to your fork on GitHub.
2. Click the "Compare & pull request" button next to your feature branch.
3. Ensure the base fork is `devondragon/SpringUserFramework` and the base branch is the issue branch you're addressing.
4. Provide a clear title and description for your PR, referencing the issue number it addresses.
5. Click "Create pull request."

Your PR will be reviewed by the maintainers. Please be responsive to feedback and willing to make adjustments as needed.


## Contributing to the Demo Frontend

If your change involves a **frontend component**, or if it requires a user interface to **showcase or test the functionality** (such as login flows, authentication UIs, or session behavior), you'll also need to contribute to the companion frontend project:  
👉 [**SpringUserFrameworkDemoApp**](https://github.com/devondragon/SpringUserFrameworkDemoApp)

This project is a lightweight web application used to **demonstrate and test** features of the `SpringUserFramework` library.

### What You Need to Do

1. **Fork the `SpringUserFrameworkDemoApp` repository** as well.
2. Make changes or add test pages in the demo app to support or demonstrate your backend work.
3. Submit a separate pull request to the `SpringUserFrameworkDemoApp` repository.
4. Reference your demo app PR in your main `SpringUserFramework` pull request description, so reviewers can test your changes end-to-end.

Keeping the demo app up to date with relevant examples helps others understand how to use the library and ensures that all features are properly tested in a real-world scenario.



## Testing Local Changes with the Demo App

Since `SpringUserFramework` is a **library**, the best way to test your changes is by using the companion project:  
👉 [**SpringUserFrameworkDemoApp**](https://github.com/devondragon/SpringUserFrameworkDemoApp)

This demo app allows you to see how the library behaves in a real application context.

### How to Test Your Changes Locally

To test updates to the library before submitting a pull request, follow these steps:

1. **Install Maven (if not already installed)**  
   You'll need Maven installed locally because Gradle publishes the library into your **local Maven cache**.

2. **Build and publish the library locally**  
   In your fork of `SpringUserFramework`, run:

   ```bash
   ./gradlew publishToMavenLocal
   ```

   This will compile the project and publish it as a `.jar` file with a `-SNAPSHOT` version into your local Maven cache (usually located at `~/.m2/repository`).

3. **Update the demo app to use your local library**  
   In your fork of [`SpringUserFrameworkDemoApp`](https://github.com/devondragon/SpringUserFrameworkDemoApp):

   - Open `build.gradle`.
   - Update the library dependency to match the `SNAPSHOT` version defined in the `gradle.properties` file from your local library project.

   Example:
   ```groovy
   implementation 'com.yourgroupid:spring-user-framework:1.2.3-SNAPSHOT'
   ```

4. **Build and run the demo app**  
   Use your IDE or run from the command line:

   ```bash
   ./gradlew bootRun
   ```

   The demo app should now load and use your locally built version of the library. This allows you to interactively test your changes before pushing them upstream.

> 💡 Make sure not to commit any version changes to `build.gradle` or `gradle.properties` as the project maintainer is the only one to update versions.


## Code Style and Standards

Please adhere to the following coding standards:

- Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for Java code.
- Use meaningful variable and method names.
- Write concise and clear comments where necessary.

Consistent code style helps maintain readability and ease of maintenance.

## Testing

Ensure that your changes do not break existing tests and include new tests as appropriate. Run all tests before submitting a PR:

```bash
./gradlew test
```


