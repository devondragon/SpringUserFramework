package com.digitalsanctuary.spring.user.ui.page;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;

public class LoginPage {

    private final SelenideElement EMAIL_FIELD = Selenide.$x("//input[@id='username']");

    private final SelenideElement PASSWORD_FIELD = Selenide.$x("//input[@id='password']");

    private final SelenideElement LOGIN_BUTTON = Selenide.$x("//button[@id='loginButton']");

    public LoginPage(String url) {
        Selenide.open(url);
    }

    public LoginSuccessPage signIn(String email, String password) {
        EMAIL_FIELD.setValue(email);
        PASSWORD_FIELD.setValue(password);
        LOGIN_BUTTON.click();
        return new LoginSuccessPage();
    }
}
