package com.digitalsanctuary.spring.user.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

public class SuccessRegisterPage {
    private final SelenideElement SUCCESS_MESSAGE = $x("//section//div//h1");

    public String message() {
        return SUCCESS_MESSAGE.text();
    }
}
