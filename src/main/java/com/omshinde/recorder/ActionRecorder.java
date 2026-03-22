package com.omshinde.recorder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ActionRecorder {

    private static final Logger log = LoggerFactory.getLogger(ActionRecorder.class);

    private final WebDriver driver;
    private final JavascriptExecutor js;
    private final List<Map<String, Object>> allActions = new ArrayList<>();
    private final String recorderScript;

    public ActionRecorder(WebDriver driver) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
        this.recorderScript = loadScript();
    }

    /**
     * Loads the recorder JavaScript from the classpath resource.
     */
    private String loadScript() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("recorder.js")) {
            if (is == null) {
                throw new IllegalStateException("recorder.js not found on classpath");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load recorder.js", e);
        }
    }

    /**
     * Injects the recorder script into the current page.
     */
    private void injectRecorderScript() {
        js.executeScript(recorderScript);
        log.info("[recorder] Script injected on: {}", driver.getCurrentUrl());
    }

    /**
     * Checks if the recorder script is still active in the current page context.
     * If the page has navigated (JS context lost), re-injects the script.
     */
    public void ensureRecorderActive() {
        try {
            Boolean injected = (Boolean) js.executeScript("return window._recorderInjected === true;");
            if (!Boolean.TRUE.equals(injected)) {
                injectRecorderScript();
                log.info("[recorder] Re-injected after page change");
            }
        } catch (Exception e) {
            // Page may be loading or unresponsive — skip this cycle
            log.debug("[recorder] ensureRecorderActive skipped: {}", e.getMessage());
        }
    }

    /**
     * Pulls all new recorded actions from the browser's JS context into the Java-side
     * list, then clears the JS array. This prevents data loss on page navigation.
     */
    @SuppressWarnings("unchecked")
    public void syncActions() {
        try {
            Boolean injected = (Boolean) js.executeScript("return window._recorderInjected === true;");
            if (!Boolean.TRUE.equals(injected)) return;

            List<Map<String, Object>> newActions = (List<Map<String, Object>>) js.executeScript(
                    "var a = window._recorder.actions.splice(0); return a;"
            );
            if (newActions != null && !newActions.isEmpty()) {
                allActions.addAll(newActions);
                log.debug("[recorder] Synced {} actions (total: {})", newActions.size(), allActions.size());
            }
        } catch (Exception e) {
            log.debug("[recorder] syncActions skipped: {}", e.getMessage());
        }
    }

    /**
     * Navigates the browser to the given URL and injects the recorder script.
     */
    public void navigateTo(String url) {
        driver.get(url);
        injectRecorderScript();
        log.info("[recorder] Navigated to: {}", url);
    }

    /**
     * Returns the total count of actions recorded so far (Java-side).
     */
    public int getActionCount() {
        return allActions.size();
    }

    /**
     * Returns all recorded actions (Java-side accumulated list).
     */
    public List<Map<String, Object>> getRecordedActions() {
        return allActions;
    }

    /**
     * Saves all recorded actions to a JSON file at the given path.
     */
    public void saveActions(String filePath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        File outputFile = new File(filePath);

        // Ensure parent directories exist
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, allActions);
        log.info("[recorder] {} actions saved to: {}", allActions.size(), filePath);
    }

}