package com.digitalsanctuary.spring.user.ui.page;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.digitalsanctuary.spring.user.ui.BaseUiTest;

public class SuccessResetPasswordPage extends BaseUiTest {
    private final SelenideElement SUCCESS_RESET_MESSAGE = Selenide.$x("//div[@class='container']//div[@class='container']//span");

    public String message() {
        return SUCCESS_RESET_MESSAGE.text();
    }
}
