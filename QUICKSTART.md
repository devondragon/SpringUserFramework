# Quickstart Guide

## Prerequisites
 - Java Development Kit (JDK) 17 or later

## Quick Note

This Framework is intended to be copied and used as a template for new projects. It is not intended to be used as a dependency.

While it would be nice to vend this as a library through Maven or Gradle, I don't belive it's possible to do so.  In order for this framework to be useful (for my needs) it needs to provide the front end pages, JS, and set Spring configurations.

If anyone knows a way to do this as a dependancy, please let me knowm, or submit a PR.


## Getting Started

1. Download this project as a zip file and extract it to a new folder.
2. Open the project in your favorite IDE.  I use VSCode.
3. Copy the `src/main/resources/application-local.yml-example` file to `src/main/resources/application-local.yml`
4. Edit the `src/main/resources/application-local.yml` file to set your configurations for things like SMTP server, Facebook or Google OAuth information, etc.  If you need to override any defaults from `application.yml` you can do so here.
5. Create the local database: `docker run -p 127.0.0.1:3306:3306 --name springuserframework -e MARIADB_ROOT_PASSWORD=springuserroot -e MARIADB_DATABASE=springuser -e MARIADB_USER=springuser -e MARIADB_PASSWORD=springuser -d mariadb:latest`
6. If you are using a public hostname for OAuth (Google or Facebook), you will need to setup an [ngrok tunnel](https://medium.com/@Demipo/exposing-a-local-spring-boot-app-with-ngrok-819250ef75f) or [CloudFlare tunnel](https://vitobotta.com/2022/02/27/free-ngrok-alternative-with-cloudflare-tunnels/)
7. Run the project.  You can do this from the command line with `./gradlew bootRun`
8. Open a browser and go to `http://localhost:8080` to see the home page.
9. If things are working, you can now develop your own application on top of this framework


## Bugs, Gaps, Questions
If you find any issues, gaps in documentation or features, or have any questions, please open an issue on GitHub!



Back to [README.md](README.md)
