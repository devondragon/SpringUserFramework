package ui.data;

import com.digitalsanctuary.spring.user.dto.UserDto;
public class UiTestData {

    public static final String ACCOUNT_EXIST_ERROR_MESSAGE = "An account for that username/email already exists. " +
            "Please enter a different email.";

    public static final String SUCCESS_SING_UP_MESSAGE = "Thank you for registering!";

    public static final String TEST_USER_ENCODED_PASSWORD = "$2a$10$RoyC5aE9etri.md4kD1Ne.9OH4aTzrb9pOQq8PN6MR8FOyObltZRG";

    public static UserDto getUserDto() {
        UserDto userDto = new UserDto();
        userDto.setFirstName("testUser");
        userDto.setLastName("userTest");
        userDto.setEmail("testUser@bk.com");
        userDto.setPassword("testUserPassword");
        userDto.setMatchingPassword("testUserPassword");
        return userDto;
    }
}
