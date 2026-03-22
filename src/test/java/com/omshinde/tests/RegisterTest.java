package com.omshinde.tests;

import com.omshinde.model.RegisterTestData;
import com.omshinde.pages.RegisterPage;
import com.omshinde.util.JsonUtil;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class RegisterTest extends AbstractTest {

    private RegisterPage registerPage;
    private RegisterTestData testData;

    @BeforeTest
    @Parameters("testDataPath")
    public void setup(String testDataPath) {
        this.registerPage = new RegisterPage(driver);
        this.testData = JsonUtil.getTestData(testDataPath, RegisterTestData.class);
    }

    @Test
    public void registerTest() {
        registerPage.goTo("https://demoqa.com/login");
        org.testng.Assert.assertTrue(registerPage.isAt());
        registerPage.clickNewUserButton();
        registerPage.enterFirstName(testData.firstName());
        registerPage.enterLastName(testData.lastName());
        registerPage.enterUserName(testData.userName());
        registerPage.enterPassword(testData.password());
        registerPage.clickRegisterButton();
    }
}