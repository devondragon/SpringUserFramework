package com.digitalsanctuary.spring.user.ui.page;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selenide;

public class LoginSuccessPage {

    private final ElementsCollection WELCOME_MESSAGE = Selenide.$$x("//section//div//span");

    public String welcomeMessage() {
        return String.format("%s %s", WELCOME_MESSAGE.get(0).text(), WELCOME_MESSAGE.get(1).text());
    }
}
