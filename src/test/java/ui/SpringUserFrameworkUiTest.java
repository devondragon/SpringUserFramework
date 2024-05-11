package ui;


import com.digitalsanctuary.spring.user.dto.UserDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ui.data.UiTestData;
import ui.jdbc.Jdbc;
import ui.page.LoginPage;
import ui.page.LoginSuccessPage;
import ui.page.RegisterPage;
import ui.page.SuccessRegisterPage;

import static ui.data.UiTestData.*;

public class SpringUserFrameworkUiTest extends BaseUiTest {

    private static final String URI = "http://localhost:8080/";

    private static final UserDto testUser = UiTestData.getUserDto();

    {
        super.setDriver(Driver.CHROME);
    }

    @AfterEach
    public void deleteTestUser() {
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
        RegisterPage registerPage = new RegisterPage(URI + "user/register.html");
        registerPage.signUp(testUser.getFirstName(), testUser.getLastName(), testUser.getEmail(),
                testUser.getPassword(), testUser.getMatchingPassword());
        String actualMessage = registerPage.accountExistErrorMessage();
        Assertions.assertEquals(ACCOUNT_EXIST_ERROR_MESSAGE, actualMessage);
    }

    /**
     * checks that welcome message in success login page contains username
     */
    @Test
    public void successSignIn() {
        Jdbc.saveTestUser(testUser);
        LoginPage loginPage = new LoginPage(URI + "user/login.html");
        LoginSuccessPage loginSuccessPage = loginPage.signIn(testUser.getEmail(), testUser.getPassword());
        String welcomeMessage = loginSuccessPage.welcomeMessage();
        String firstName = testUser.getFirstName();
        Assertions.assertTrue(welcomeMessage.contains(firstName));
    }


}
