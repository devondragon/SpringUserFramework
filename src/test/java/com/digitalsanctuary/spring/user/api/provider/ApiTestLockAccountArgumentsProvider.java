package com.digitalsanctuary.spring.user.api.provider;

import com.digitalsanctuary.spring.user.api.data.ApiTestData;
import com.digitalsanctuary.spring.user.api.data.DataStatus;
import com.digitalsanctuary.spring.user.api.provider.holder.ApiTestArgumentsHolder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.util.stream.Stream;

public class ApiTestLockAccountArgumentsProvider implements ArgumentsProvider {
  @Override
  public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
    return Stream.of(
            new ApiTestArgumentsHolder(
                    ApiTestData.getLockAccountDto(),
                    DataStatus.VALID,
                    ApiTestData.successLockAccount()
            ),

            new ApiTestArgumentsHolder(
                    ApiTestData.getEmptyLockAccountDto(),
                    DataStatus.INVALID,
                    ApiTestData.invalidBodyLockAccountFailry()
            ),

            new ApiTestArgumentsHolder(
                    ApiTestData.getLockAccountDtoForMissingUser(),
                    DataStatus.NOT_FOUND,
                    ApiTestData.lockAccountFailry()
            )
    ).map(Arguments::of);
  }
}
