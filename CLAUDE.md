# Spring User Framework - Developer Guide

## Commands
- **Build**: `./gradlew build`
- **Run Tests**: `./gradlew test`
- **Run Single Test**: `./gradlew test --tests "com.digitalsanctuary.spring.user.service.UserServiceTest"`
- **Test with JDK17**: `./gradlew testJdk17`
- **Test with JDK21**: `./gradlew testJdk21`
- **Test All JDKs**: `./gradlew testAll`
- **Lint/Check**: `./gradlew check`
- **Publish Locally**: `./gradlew publishLocal`

## Code Style Guidelines
- **Imports**: Organize imports alphabetically, no wildcards
- **Formatting**: Use proper indentation (4 spaces)
- **Documentation**: Use JavaDoc for all public classes and methods
- **Naming**: CamelCase for classes, lowerCamelCase for methods/variables
- **Logging**: Use SLF4J (Lombok @Slf4j) with appropriate log levels
- **Error Handling**: Use specific exceptions, provide meaningful messages
- **Nullability**: Check for null before accessing potentially null references
- **Services**: Use @RequiredArgsConstructor with final fields for DI