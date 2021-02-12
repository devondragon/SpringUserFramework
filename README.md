# SpringUserFramework
A Easy to leverage User Management Framework based on Spring Security

## Summary
This is an easy to use starter application or framework for handling basic user management features for your Spring based Web Application.  It provides registration, with optional email verification, login, logout, and forgot password flows.  There are basic example pages for everything, unstyled, to allow for the easiest integration to your application.  

## Goals
- To build an easy to use starting point for any Spring based web application that needs user features.
- To provide a local database backed user store (although SSO integrations are easy to add using Spring Security).
- To design based on REST APIs
- To build on top of Spring Security to provide the best security and make it easy to leverage Spring Security features such as 2FA and SSO integrations.
- To make it easily configurable using applicaiton.properties when possible
- To use the messages feature for all user facing text and messaging, so internationalization is straight forward.
- To provide an audit event framework to make security audit trails easy to deliver.
- To use email address as the username by default.

## Features
At the highest level the framework provides support for, and working examples of, registration, with or without email verification, log in, log out, and forgot password flows.  

It uses a database for the user store, and leverages Spring JPA to make it easy to use any database you like.

Via simple configuration you can setup Spring Security to either block anonymous access to pages, excepting a whitelist of URIs (the most secure configuration) or to allow access by default, and configure a list of URIs to protect and require a logged in user session to access.

CSRF is enabled by default and the example jQuery AJAX calls pass the CSRF token from the Thymeleaf page context.


## Notes
Much of this is based on the [Baeldung course on Spring Security]( https://www.baeldung.com/learn-spring-security-course).  If you want to learn more about Spring Security or you'd like to add an SSO integration or add 2FA, that guide is a great place to get started!

There is no warranty or garantee of functionaltiy, quality, performance, or security made by the author.  This code is availble freely but you take all responsibilty and liabilty for your application.
