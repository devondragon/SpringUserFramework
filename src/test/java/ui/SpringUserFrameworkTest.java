package ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ui.jdbc.Jdbc;
import ui.page.RegisterPage;
import ui.page.SuccessRegisterPage;

import static ui.data.UserTestData.*;

public class SpringUserFrameworkTest extends BaseTest {
    private static final String SUCCESS_MESSAGE = "Thank you for registering!";
    private static final String URI = "http://localhost:8080/";

    @AfterAll
    public static void deleteTestUser() {
      Jdbc.deleteTestUser();
    }

    @Test
    public void successSignUp() {
        RegisterPage registerPage = new RegisterPage(URI + "user/register.html");
        registerPage.signUp(FIRST_NAME, LAST_NAME, EMAIL, PASSWORD, CONFIRM_PASSWORD);
        SuccessRegisterPage successRegisterPage = new SuccessRegisterPage();
        String actual = successRegisterPage.message();
        Assertions.assertEquals(actual, SUCCESS_MESSAGE);
    }
}
