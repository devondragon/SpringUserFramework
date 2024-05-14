package com.digitalsanctuary.spring.user.ui;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseUiTest {
    private Driver driver;

    public enum Driver {
        CHROME("chrome"),
        OPERA("opera"),
        FIREFOX("firefox"),
        EDGE("edge");

        private final String browser;

        Driver(String browser) {
            this.browser = browser;
        }
    }

    public void setUp() {
        switch (this.driver) {
            case CHROME -> WebDriverManager.chromedriver().setup();
            case OPERA -> WebDriverManager.operadriver().setup();
            case FIREFOX -> WebDriverManager.firefoxdriver().setup();
            case EDGE -> WebDriverManager.edgedriver().setup();
        }
        Configuration.browser = driver.browser;
        Configuration.browserSize = "2560x1440";
        Configuration.webdriverLogsEnabled = true;
        Configuration.headless = false;
    }

    @BeforeEach
    public void init() {
        setUp();
    }

    @AfterEach
    public void tearDown() {
        Selenide.closeWebDriver();
    }

    void setDriver(Driver driver) {
        this.driver = driver;
    }

}
