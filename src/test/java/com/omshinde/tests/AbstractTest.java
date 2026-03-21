package com.omshinde.tests;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractTest {

    protected WebDriver driver;

    @BeforeTest
    public void setDriver() {
        WebDriverManager.chromedriver().setup();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--disable-password-manager-reauthentication");
        options.addArguments("--disable-notifications");
        options.addArguments("--start-maximized");
        this.driver = new ChromeDriver(options);
        this.driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        this.driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
    }

    @AfterTest
    public void quitDriver() {
        if (this.driver != null) {
            this.driver.quit();
        }
    }

}