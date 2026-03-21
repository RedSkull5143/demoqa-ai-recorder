package com.omshinde.tests;

import com.omshinde.model.LoginTestData;
import com.omshinde.pages.BookStorePage;
import com.omshinde.pages.LoginPage;
import com.omshinde.util.JsonUtil;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class LoginTest extends AbstractTest {

    private LoginPage loginPage;
    private BookStorePage bookStorePage;
    private LoginTestData testData;

    @BeforeTest
    @Parameters("testDataPath")
    public void setPageObjects(String testDataPath) {
        this.loginPage = new LoginPage(driver);
        this.bookStorePage = new BookStorePage(driver);
        this.testData = JsonUtil.getTestData(testDataPath, LoginTestData.class);
        Assert.assertNotNull(testData, "Test data failed to load. Check testDataPath in suite XML.");
    }

    @Test
    public void loginTest() {
        loginPage.goTo("https://demoqa.com/login");
        Assert.assertTrue(loginPage.isAt(), "Login page did not load as expected.");
        loginPage.login(testData.username(), testData.password());
    }

    @Test(dependsOnMethods = "loginTest")
    public void bookStoreTest() {
        Assert.assertTrue(bookStorePage.isAt(), "Book Store page was not visible after login.");
    }

    @Test(dependsOnMethods = "bookStoreTest")
    public void logoutTest() {
        bookStorePage.logout();
        Assert.assertTrue(loginPage.isAt(), "Login page was not displayed after logout.");
    }

}