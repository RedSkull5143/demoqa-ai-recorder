package com.omshinde.tests;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.omshinde.recorder.ActionRecorder;

import io.github.bonigarcia.wdm.WebDriverManager;

public class RecorderTest {

    public static void main(String[] args) throws Exception {

        // ── Load configuration ──
        Properties config = new Properties();
        try (InputStream is = RecorderTest.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new IllegalStateException("config.properties not found on classpath");
            }
            config.load(is);
        }

        String startUrl = config.getProperty("base.url");
        String outputPath = config.getProperty("recorder.output", "recorded-actions.json");

        // ── Setup Chrome driver ──
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

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

        // ── Start recording ──
        ActionRecorder recorder = new ActionRecorder(driver);
        recorder.navigateTo(startUrl);

        System.out.println("==========================================================");
        System.out.println("  RECORDING STARTED");
        System.out.println("  URL: " + startUrl);
        System.out.println("  Interact with the browser now.");
        System.out.println("  Press ENTER in this terminal to stop recording...");
        System.out.println("==========================================================");

        // ── Background sync thread: keeps recorder alive across page navigations ──
        Thread syncThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    recorder.syncActions();
                    recorder.ensureRecorderActive();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Browser may be navigating or unresponsive — skip this cycle
                }
            }
        });
        syncThread.setDaemon(true);
        syncThread.start();

        // ── Wait for user to press Enter ──
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }

        // ── Stop recording ──
        syncThread.interrupt();

        // Final sync to capture any remaining actions
        try {
            recorder.syncActions();
        } catch (Exception e) {
            // Browser may already be closed
        }

        int totalActions = recorder.getActionCount();

        if (totalActions == 0) {
            System.out.println("[recorder] No actions were recorded.");
        } else {
            recorder.saveActions(outputPath);
        }

        System.out.println("==========================================================");
        System.out.println("  RECORDING STOPPED");
        System.out.println("  Total actions captured: " + totalActions);
        if (totalActions > 0) {
            System.out.println("  Saved to: " + outputPath);
        }
        System.out.println("==========================================================");

        driver.quit();
    }

}
