# Development Guide for SpringUserFramework

Welcome to the development guide for the SpringUserFramework and its accompanying Demo Application. This document provides a comprehensive walkthrough for setting up your development environment, making changes to the library, and testing those changes using the demo application.

## Table of Contents

- [Development Guide for SpringUserFramework](#development-guide-for-springuserframework)
  - [Table of Contents](#table-of-contents)
  - [Prerequisites](#prerequisites)
  - [Forking the Repositories](#forking-the-repositories)
  - [Cloning the Repositories](#cloning-the-repositories)
  - [Setting Up the Development Environment](#setting-up-the-development-environment)
    - [Building the Library](#building-the-library)
    - [Configuring the Demo Application](#configuring-the-demo-application)
  - [Database Configuration](#database-configuration)
    - [Using Docker Compose](#using-docker-compose)
    - [Manual Setup](#manual-setup)
  - [Running the Demo Application](#running-the-demo-application)
  - [Implementing Changes](#implementing-changes)
    - [Updating the Library](#updating-the-library)
    - [Testing with the Demo Application](#testing-with-the-demo-application)
  - [Writing Unit Tests](#writing-unit-tests)
  - [Contributing](#contributing)

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 17 or higher**: [Download JDK](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
- **Gradle**: [Install Gradle](https://gradle.org/install/)
- **Docker** (optional, for database setup): [Get Docker](https://www.docker.com/get-started)
- **MariaDB** (if not using Docker): [MariaDB Installation Guide](https://mariadb.com/kb/en/getting-installing-and-upgrading-mariadb/)

## Forking the Repositories

To contribute to the development of the SpringUserFramework and its Demo Application:

1. **Fork the Repositories**:
   - Navigate to the [SpringUserFramework repository](https://github.com/devondragon/SpringUserFramework) and click on the "Fork" button in the upper right corner.
   - Repeat the process for the [SpringUserFrameworkDemoApp repository](https://github.com/devondragon/SpringUserFrameworkDemoApp).

## Cloning the Repositories

Once you've forked the repositories:

1. **Clone the Repositories Locally**:
   ```bash
   git clone https://github.com/your-username/SpringUserFramework.git
   git clone https://github.com/your-username/SpringUserFrameworkDemoApp.git
   ```

   Replace `your-username` with your GitHub username.

## Setting Up the Development Environment

### Building the Library

1. **Navigate to the SpringUserFramework Directory**:
   ```bash
   cd SpringUserFramework
   ```

2. **Create a New Branch for Your Changes**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Build and Publish the Library Locally**:
   ```bash
   ./gradlew publishLocal
   ```

   This command publishes the library to your local Maven repository, making it available for the Demo Application.

### Configuring the Demo Application

1. **Navigate to the SpringUserFrameworkDemoApp Directory**:
   ```bash
   cd ../SpringUserFrameworkDemoApp
   ```

2. **Create a New Branch for Your Changes**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Update the `build.gradle` File**:
   - Open the `build.gradle` file.
   - Locate the dependency for the SpringUserFramework library.
   - Update the version to match the `-SNAPSHOT` version you published locally. For example:
     ```groovy
     implementation 'com.digitalsanctuary:ds-spring-user-framework:3.0.1-SNAPSHOT'
     ```

## Database Configuration

The Demo Application requires a MariaDB database. You can set this up using Docker Compose or manually.

### Using Docker Compose

1. **Navigate to the Demo Application Directory**:
   ```bash
   cd ../SpringUserFrameworkDemoApp
   ```

2. **Start the Docker Compose Stack**:
   ```bash
   docker-compose up -d
   ```

   This command starts the MariaDB database defined in the `docker-compose.yml` file.

### Manual Setup

If you prefer to set up the database manually:

1. **Install MariaDB**: Follow the [MariaDB Installation Guide](https://mariadb.com/kb/en/getting-installing-and-upgrading-mariadb/).

2. **Create a Database and User**:
   ```sql
   CREATE DATABASE spring_user_framework_demo;
   CREATE USER 'demo_user'@'localhost' IDENTIFIED BY 'password';
   GRANT ALL PRIVILEGES ON spring_user_framework_demo.* TO 'demo_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

3. **Configure the Application**:
   - Open the `application.properties` or `application.yml` file in the Demo Application.
   - Update the database connection properties to match your setup. For example:
     ```properties
     spring.datasource.url=jdbc:mariadb://localhost:3306/spring_user_framework_demo
     spring.datasource.username=demo_user
     spring.datasource.password=password
     ```

## Running the Demo Application

1. **Navigate to the Demo Application Directory**:
   ```bash
   cd ../SpringUserFrameworkDemoApp
   ```

2. **Run the Application**:
   ```bash
   ./gradlew bootRun
   ```

   The application should now start, connecting to the MariaDB database.

## Implementing Changes

### Updating the Library

1. **Navigate to the SpringUserFramework Directory**:
   ```bash
   cd ../SpringUserFramework
   ```

2. **Make Your Changes**: Implement the desired features or fixes in the library codebase.

3. **Build and Publish the Updated Library Locally**:
   ```bash
   ./gradlew publishLocal
   ```

### Testing with the Demo Application

1. **Navigate to the Demo Application Directory**:
   ```bash
   cd ../SpringUserFrameworkDemoApp
   ```

2. **Ensure the `build.gradle` File References the Updated Snapshot Version**.

3. **Run the Application**:
   ```bash
   ./gradlew bootRun
   ```

4. **Test Your Changes**: Verify that your updates to the library are functioning as intended within the Demo Application.

## Writing Unit Tests

To maintain code quality:

1. **Add Unit Tests**: Write tests for new or modified functionality in both the library and the Demo Application as applicable.

2. **Run Tests**:
   ```bash
   ./gradlew test
   ```

   Ensure all tests pass before committing your changes.



## Contributing

If you wish to contribute your changes back to the main repositories:

1. **Commit Your Changes**:
   ```bash
   git add .
   git commit -m "Description of your changes"
   ```

2. **Push to Your Fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Submit a Pull Request (PR)**:
   - Go to **your fork** of the repository on GitHub.
   - You should see a prompt to submit a **Pull Request** (PR) to the original repository ([SpringUserFramework](https://github.com/devondragon/SpringUserFramework) or [SpringUserFrameworkDemoApp](https://github.com/devondragon/SpringUserFrameworkDemoApp)).
   - Click **Compare & pull request**.
   - Provide a **clear description** of your changes, including:
     - What problem or enhancement your changes address.
     - Any dependencies or related changes in the **Demo App** (if applicable).
     - Instructions for testing (if necessary).
   - Submit the PR and wait for a review.

4. **Respond to Feedback**:
   - If requested changes are needed, update your branch and push changes.
   - GitHub will automatically update the PR.

5. **Merge Process**:
   - Once your PR is approved, it will be merged into the main repository.
   - If additional work is required before merging, a maintainer will guide you.
