package com.digitalsanctuary.spring.user.api.provider;

import com.digitalsanctuary.spring.user.api.data.ApiTestData;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.util.stream.Stream;

public class ApiTestRegistrationArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
        return Stream.of(
                new ApiTestRegistrationArgumentsHolder(
                        ApiTestData.getUserDto(),
                        ApiTestRegistrationArgumentsHolder.UserStatus.NEW,
                        ApiTestData.successRegistration()
                ),

                new ApiTestRegistrationArgumentsHolder(
                        ApiTestData.getUserDto(),
                        ApiTestRegistrationArgumentsHolder.UserStatus.EXIST,
                        ApiTestData.userAlreadyExist()
                )

        ).map(Arguments::of);
    }
}
