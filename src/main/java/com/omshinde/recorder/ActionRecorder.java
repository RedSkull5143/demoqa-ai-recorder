package com.omshinde.recorder;

import java.io.File;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ActionRecorder {

    private final WebDriver driver;
    private final JavascriptExecutor js;

    public ActionRecorder(WebDriver driver) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
    }

    private void injectRecorderScript() {
        String script = """
                window.recordedActions = window.recordedActions || [];
                        document.addEventListener('click', function(e) {
                            var el = e.target;
                            var ignoreTags = ['FORM', 'DIV', 'BODY', 'SPAN', 'LABEL'];
                            if (ignoreTags.indexOf(el.tagName) !== -1) return;
                            window.recordedActions.push({
                                action: 'click',
                                tag: el.tagName,
                                id: el.id,
                                name: el.name,
                                text: el.innerText.trim().substring(0, 50)
                            });
                        }, true);

                        document.addEventListener('input', function(e) {
                            var el = e.target;
                            var existing = window.recordedActions.findIndex(
                                a => a.action === 'input' && a.id === el.id
                            );
                            var entry = {
                                action: 'input',
                                tag: el.tagName,
                                id: el.id,
                                name: el.name,
                                value: el.value
                            };
                            if (existing !== -1) {
                                window.recordedActions[existing] = entry;
                            } else {
                                window.recordedActions.push(entry);
                            }
                        }, true);
                        window._recorderInjected = true;
                        """;
        this.js.executeScript(script);
    }

    public void ensureRecorderActive() {
        Boolean injected = (Boolean) this.js.executeScript("return window._recorderInjected === true;");
        if (!injected) {
            injectRecorderScript();
            System.out.println("[recorder] Re-injected after page change");
        }
    }

    public void navigateTo(String url) {
        driver.get(url);
        injectRecorderScript();
        System.out.println("[recorder] Navigated to: " + url);
    }

    public Object getRecordedActions() {
        return this.js.executeScript("return window.recordedActions;");
    }

    public void saveActions(String filePath) throws Exception {
        Object actions = getRecordedActions();
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(filePath), actions);
        System.out.println("[recorder] Actions saved to: " + filePath);
    }

}