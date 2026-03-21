package com.omshinde.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class BookStorePage extends AbstractPage {

    @FindBy(id = "userName-value")
    private WebElement userLabel;

    @FindBy(id = "submit")
    private WebElement logoutButton;

    public BookStorePage(WebDriver driver) {
        super(driver);  
    }

    @Override
    public boolean isAt() {
        this.wait.until(ExpectedConditions.visibilityOf(this.userLabel));
        return this.userLabel.isDisplayed();
    }

    public String getLoggedInUser() {
        return this.userLabel.getText();
    }

    public void logout() throws InterruptedException {
        Thread.sleep(5000);
        this.logoutButton.click();
    }

}