package com.omshinde.tests;

import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.omshinde.recorder.ActionRecorder;

public class RecorderTest {

    public static void main(String args[]) throws Exception {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);

        ActionRecorder ar = new ActionRecorder(driver);
        ar.navigateTo("https://demoqa.com/login");

        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            ar.ensureRecorderActive();
            Thread.sleep(500);
        }
        ar.saveActions("recorded-actions.json");
        driver.quit();
    }

}
