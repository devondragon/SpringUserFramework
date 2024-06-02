package com.digitalsanctuary.spring.user.ui.page;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.digitalsanctuary.spring.user.ui.BaseUiTest;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$x;

public class ForgotPasswordPage extends BaseUiTest {
    private final SelenideElement EMAIL_FIELD = $(By.id("email"));
    private final SelenideElement SUBMIT_BTN = $x("//button");

    public ForgotPasswordPage(String url) {
        Selenide.open(url);
    }

    public ForgotPasswordPage fillEmail(String email) {
        EMAIL_FIELD.setValue(email);
        return this;
    }

    public SuccessResetPasswordPage clickSubmitBtn() {
        SUBMIT_BTN.click();
        return new SuccessResetPasswordPage();
    }

}
