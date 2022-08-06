# SpringUserFramework
An Easy to leverage Java Spring Boot User Management Framework based on [Spring Security](https://spring.io/projects/spring-security)

## Summary
This is an easy to use starter application or framework for handling basic user management features for your [Spring](https://spring.io/) based Web Application.  It provides registration, with optional email verification, login, logout, and forgot password flows.  There are basic example pages for everything, unstyled, to allow for the easiest integration to your application.  

## Goals
- To build an easy to use starting point for any Spring based web application that needs user features.
- To provide a local database backed user store (although SSO integrations are easy to add using Spring Security).
- To design based on REST APIs
- To build on top of Spring Security to provide the best security and make it easy to leverage Spring Security features such as 2FA and SSO integrations.
- To make it easily configurable using application.properties when possible
- To use the messages feature for all user facing text and messaging, so that internationalization is straight forward.
- To provide an audit event framework to make security audit trails easy to deliver.
- To use email address as the username by default.

## Features
At the highest level the framework provides support for, and working examples of, registration, with or without email verification, log in, log out, and forgot password flows.  

It uses a database for the user store, and leverages Spring JPA to make it easy to use any database you like.

Via simple configuration you can setup Spring Security to either block anonymous access to pages, excepting a whitelist of URIs (the most secure configuration) or to allow access by default, and configure a list of URIs to protect and require a logged in user session to access.

CSRF is enabled by default and the example jQuery AJAX calls pass the CSRF token from the Thymeleaf page context.

An audit event and listener are implmented to allow for recording security events, or any type of event you like, and logging them to a seperate file. You can easily replace the logging listener with your own and store audit events in a database, publish them to a REST API, or anything else.

There is Role and Privilege setup service, which allows you to easily define Roles, associated Privileges, and Role inheritance hierachy in your application.yml. Check out the application.yml for the basic OOTB configuration, and look at the RolePrivilegeSetupService component.  You can still create and leverage roles and privileges programatically, but this makes it easy to define and see the associations.  


## How To Get Started

### Database
This framework uses a database as a user store. By buildling on top of Spring JPA it is easy to use which ever datastore you like. The example configuration in application.properties is for a [MariaDB](https://mariadb.com) 10.5 database. You will need to create a user and a database and configure the database name, username, and password.

You can do this using docker with a command like this:

docker run -p 127.0.0.1:3306:3306 --name springuserframework -e MARIADB_ROOT_PASSWORD=springuserroot -e MARIADB_DATABASE=springuser -e MARIADB_USER=springuser -e MARIADB_PASSWORD=springuser -d mariadb:latest

### Mail Sending (SMTP)
The framework sends emails for verficiation links, forgot password flow, etc... so you need to configure the outbound SMTP server and authentication information.  

### New Relic
Out of the box the project includes the New Relic Telemetry module, and as such requires a New Relic account id, and associated API key.  If you don't use New Relic you can remove the dependancy from the build.gradle file and ignore the configuration values.

Beyond that the default configurations should be all you need, although of course you can customize things however you like.  

## Docker

After running 'gradle build', you can build a simple docker image with the application using the included Dockerfile.  It's very basic and is not using layering, buildpacks, or other things you may want for real applications.

I have also included a docker-compose file which will launch a stack with the Spring Boot Application, MariaDB Database, and Postfix Mail Server, with basic configurations in place to make everything work.  Sending email from your computer (via the docker Postfix Mail Server) will likely get blocked by GMail, Outlook, etc... due to spam checks.  You can always test by using [10MinuteMail.com](https://10MinuteMail.com) addresses, but for real use you should probably leave off the mail server entirely and configure the Spring Boot application to use a real mail server for outbound transactional emails.  

## Notes
Much of this is based on the [Baeldung course on Spring Security](https://www.baeldung.com/learn-spring-security-course).  If you want to learn more about Spring Security or you'd like to add an SSO integration or add 2FA, that guide is a great place to get started!

You will see examples of different ways to to serve and consume the APIs in the codebase. For example some of the APIs return 200 response for all queries with a success flag and status codes to convey success or failures. Whereas others only use the 200 response on success, and use 409 or 500 for various error scenarios.  Some AJAX client JS will trigger a redirect to a new page, whereas other client JS will display messaging directly on the current page.  I think there are good reasons you may wish to use one or another approach, so I wanted to provide working examples of each.  

There is no warranty or garantee of functionaltiy, quality, performance, or security made by the author.  This code is availble freely but you take all responsibilty and liabilty for your application.
