package ui.page;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;

/**
 * Register page
 */
public class RegisterPage {
    private final SelenideElement FIRST_NAME_FIELD = Selenide.$x("//input[@id='firstName']");
    private final SelenideElement LAST_NAME_FIELD = Selenide.$x("//input[@id='lastName']");
    private final SelenideElement EMAIL_FIELD = Selenide.$x("//input[@id='email']");
    private final SelenideElement PASSWORD_FIELD = Selenide.$x("//input[@id='password']");
    private final SelenideElement CONFIRM_PASSWORD_FIELD = Selenide.$x("//input[@id='matchPassword']");
    private final SelenideElement SIGN_UP_BUTTON = Selenide.$x("//button[@id='signUpButton']");

    public RegisterPage(String url) {
        Selenide.open(url);
    }
    /**
     * Filling register form and click signUp button
     */
    public void signUp(String firstName, String lastName, String email, String password, String confirmPassword) {
        FIRST_NAME_FIELD.setValue(firstName);
        LAST_NAME_FIELD.setValue(lastName);
        EMAIL_FIELD.setValue(email);
        PASSWORD_FIELD.setValue(password);
        CONFIRM_PASSWORD_FIELD.setValue(confirmPassword);
        SIGN_UP_BUTTON.click();
    }

}
