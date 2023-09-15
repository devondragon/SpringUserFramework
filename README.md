# SpringUserFramework

SpringUserFramework is a Java Spring Boot User Management Framework designed to simplify the implementation of user management features in your Spring-based web application. It is built on top of [Spring Security](https://spring.io/projects/spring-security) and provides out-of-the-box support for registration, login, logout, and forgot password flows. The framework includes basic example pages that are unstyled, allowing for seamless integration into your application.

## Summary

This framework aims to achieve the following goals:
- Provide an easy-to-use starting point for any Spring-based web application that requires user management features.
- Offer a local database-backed user store, with the flexibility to integrate Single Sign-On (SSO) using Spring Security.
- Design the framework around REST APIs.
- Utilize Spring Security for enhanced security features, such as two-factor authentication (2FA) and SSO integrations.
- Enable easy configuration through the use of `application.yml` whenever possible.
- Support internationalization by utilizing the messages feature for all user-facing text and messaging.
- Provide an audit event framework to facilitate the generation of security audit trails.
- Use the email address as the default username.

## Features

The framework provides support for the following features:
- Registration, with optional email verification.
- Login and logout functionality.
- Forgot password flow.
- Database-backed user store using Spring JPA.
- Configuration options to control anonymous access, whitelist URIs, and protect specific URIs requiring a logged-in user session.
- CSRF protection enabled by default, with example jQuery AJAX calls passing the CSRF token from the Thymeleaf page context.
- Audit event framework for recording and logging security events, customizable to store audit events in a database or publish them via a REST API.
- Role and Privilege setup service to define roles, associated privileges, and role inheritance hierarchy using `application.yml`.



## How To Get Started

### Database
This framework uses a database as a user store. By buildling on top of Spring JPA it is easy to use which ever datastore you like. The example configuration in application.yml is for a [MariaDB](https://mariadb.com) 10.5 database. You will need to create a user and a database and configure the database name, username, and password.

You can do this using docker with a command like this:

docker run -p 127.0.0.1:3306:3306 --name springuserframework -e MARIADB_ROOT_PASSWORD=springuserroot -e MARIADB_DATABASE=springuser -e MARIADB_USER=springuser -e MARIADB_PASSWORD=springuser -d mariadb:latest

Or on Apple Silicon:

docker run -p 127.0.0.1:3306:3306 --name springuserframework -e MARIADB_ROOT_PASSWORD=springuserroot -e MARIADB_DATABASE=springuser -e MARIADB_USER=springuser -e MARIADB_PASSWORD=springuser -d arm64v8/mariadb:latest


### Mail Sending (SMTP)
The framework sends emails for verficiation links, forgot password flow, etc... so you need to configure the outbound SMTP server and authentication information.

### SSO OAuth2 with Google and Facebook
The framework supports SSO OAuth2 with Google and Facebook.  To enable this you need to configure the client id and secret for each provider.

For local development you will need a public hostname and HTTPS enabled.  You can use ngrok to create a public hostname and tunnel to your local machine.  You can then use the ngrok hostname in your Google and Facebook developer console configuration.

There is an example configuration file in /src/main/resources called application-local.yml-example.  By default this project's gradle bootRun command runs Spring using the "local" profile.  So you can just copy that file to application-local.yml and replace the values (keys, URLs, etc..) with your values.  If you are using a different profile to run (such as default) you will just need to ensure the same configs are in place in your active configuration file(s).  

Missing or incorrect configuration values will make this framework not work correctly.  


### New Relic
Out of the box the project includes the New Relic Telemetry module, and as such requires a New Relic account id, and associated API key.  If you don't use New Relic you can remove the dependancy from the build.gradle file and ignore the configuration values.

Beyond that the default configurations should be all you need, although of course you can customize things however you like.

## Docker

After running gradle build, you can build a simple Docker image of the application using the provided Dockerfile. Please note that this Dockerfile is basic and does not incorporate advanced features such as layering or buildpacks that you may require for production applications.

Additionally, a docker-compose file is included, which launches a stack with the Spring Boot Application, MariaDB Database, and Postfix Mail Server. The configurations in the docker-compose file are set to make everything work smoothly. However, please be aware that sending emails from your computer (via the docker Postfix Mail Server) may be blocked by email providers due to spam checks. You can use temporary email addresses from [10MinuteMail.com](https://10minutemail.com) for testing purposes, but for real use, it is recommended to configure the Spring Boot application to use a real mail server for outbound transactional emails.




## Dev Tools

### SpringBoot DevTools Auto Restart and Live Reload
Read the following articles:
 - https://www.digitalsanctuary.com/java/springboot-devtools-auto-restart-and-live-reload.html
 - https://www.digitalsanctuary.com/java/how-to-get-springboot-livereload-working-over-https.html

### Live Reload over HTTPS Setup
If you are running your local dev env using HTTPS or referencing it from a ngrok tunnel using HTTPS, you will need to make a few changes to get Live Reload to work. First you need to comment out the LiveReload HTTP line near the bottom of the index.html Thymeleaf template file.  And uncomment the HTTPS line just below.

You then need to install mitmproxy and configure it to intercept the HTTPS traffic.  You can do this by running the following command:

mitmproxy --mode reverse:http://localhost:35729 -p 35739

By default, mitmproxy uses self-signed SSL certificates, so you need to tell your browser to trust them before this will work. You can do this by opening https://localhost:35739/livereload.js in your browser and going through the steps to trust the server and certificate. Alternatively, you can configure mitmproxy to use real certificates and avoid this step. Follow these directions: https://docs.mitmproxy.org/stable/concepts-certificates/

## Notes
Much of this is based on the [Baeldung course on Spring Security](https://www.baeldung.com/learn-spring-security-course).  If you want to learn more about Spring Security or would like to add SSO integration or 2FA to your application, that guide is a great place to start.

The codebase provides examples of different ways to serve and consume APIs. For instance, some APIs return a 200 response for all queries with a success flag and use status codes to convey success or failures. Others only use the 200 response for successful requests and use 409 or 500 for various error scenarios. The AJAX client JavaScript in the codebase also showcases different approaches, with some triggering redirects to new pages while others display messaging directly on the current page. These examples aim to demonstrate different implementation options depending on your preferences and requirements.

Please note that there is no warranty or guarantee of functionality, quality, performance, or security made by the author. The code is available freely, but you assume all responsibility and liability for its usage in your application.
