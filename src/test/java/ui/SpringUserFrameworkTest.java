package ui;

import com.digitalsanctuary.spring.user.dto.UserDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ui.data.UiTestData;
import ui.jdbc.Jdbc;
import ui.page.RegisterPage;
import ui.page.SuccessRegisterPage;

import static ui.data.UiTestData.*;

public class SpringUserFrameworkTest extends BaseTest {

    private static final String URI = "http://localhost:8080/";

    private static final UserDto testUser = UiTestData.getUserDto();

    @AfterAll
    public static void deleteTestUser() {
      Jdbc.deleteTestUser(testUser);
    }

    @Test
    public void successSignUp() {
        SuccessRegisterPage registerPage = new RegisterPage(URI + "user/register.html")
                .signUp(testUser.getFirstName(), testUser.getLastName(), testUser.getEmail(),
                        testUser.getPassword(), testUser.getMatchingPassword());
        String actualMessage = registerPage.message();
        Assertions.assertEquals(SUCCESS_SING_UP_MESSAGE, actualMessage);
    }

    @Test
    public void userAlreadyExistSignUp() {
        Jdbc.saveTestUser(testUser);
        RegisterPage page = new RegisterPage(URI + "user/register.html");
        page.signUp(testUser.getFirstName(), testUser.getLastName(), testUser.getEmail(),
                testUser.getPassword(), testUser.getMatchingPassword());
        String actualMessage = page.accountExistErrorMessage();
        Assertions.assertEquals(ACCOUNT_EXIST_ERROR_MESSAGE, actualMessage);
    }

}
