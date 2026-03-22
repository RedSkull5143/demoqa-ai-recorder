FRAMEWORK_RULEBOOK = """
================================================================
FRAMEWORK RULEBOOK — Read this before generating ANY code
================================================================

## 1. PACKAGE STRUCTURE
- Page Objects → com.omshinde.pages
- Test classes → com.omshinde.tests
- Test data models → com.omshinde.model
- Utilities → com.omshinde.util

## 2. PAGE OBJECT RULES
- Every Page Object extends AbstractPage
- Constructor always calls super(driver)
- Every Page Object has isAt() method that:
    - Waits for a key element using: this.wait.until(ExpectedConditions.visibilityOf(element))
    - Returns: return element.isDisplayed()
- Elements declared as private WebElement using @FindBy annotation
- NEVER use driver.findElement() inside Page Object
- Navigation method is always: public void goTo(String url) { this.driver.get(url); }
- Method naming conventions:
    - click actions → verb + noun: login(), logout(), register(), searchFlights()
    - input actions → enter + noun: enterUserDetails(), enterAddress(), enterUserCredentials()
    - get text → get + noun: getMonthlyEarning(), getFirstName(), getPrice()
    - check presence → is + noun: isAt()

## 3. TEST DATA RULES
- Test data uses Java records (NOT classes with getters/setters)
- Record fields accessed as: testData.username() NOT testData.getUsername()
- Test data loaded via: JsonUtil.getTestData(testDataPath, YourRecord.class)
- Test data JSON stored in: src/test/resources/test-data/
- JSON keys match record field names exactly

## 4. ABSTRACT TEST RULES
- All test classes extend AbstractTest
- AbstractTest handles driver setup via @BeforeTest — NEVER call setDriver() manually
- AbstractTest handles driver quit via @AfterTest — NEVER call driver.quit() in test class
- driver is protected field — available directly in test class

## 5. TEST CLASS RULES
- Setup method always has BOTH annotations:
    @BeforeTest
    @Parameters("testDataPath")
    public void setPageObjects(String testDataPath) { ... }
- Page Objects instantiated in setup: this.loginPage = new LoginPage(driver)
- Test data loaded in setup: this.testData = JsonUtil.getTestData(testDataPath, LoginTestData.class)
- Tests chained using dependsOnMethods:
    @Test(dependsOnMethods = "loginTest")
- Every test method starts with page.isAt() assertion:
    Assert.assertTrue(loginPage.isAt())
- Navigation always happens in first test method, not setup:
    loginPage.goTo("https://demoqa.com/login")
- Assertions use TestNG Assert:
    Assert.assertTrue(...)
    Assert.assertEquals(...)

## 6. CORRECT TEST STRUCTURE TEMPLATE
public class XxxTest extends AbstractTest {

    private XxxPage xxxPage;
    private XxxTestData testData;

    @BeforeTest
    @Parameters("testDataPath")
    public void setPageObjects(String testDataPath) {
        this.xxxPage = new XxxPage(driver);
        this.testData = JsonUtil.getTestData(testDataPath, XxxTestData.class);
    }

    @Test
    public void firstTest() {
        xxxPage.goTo("URL_HERE");
        Assert.assertTrue(xxxPage.isAt());
        xxxPage.someAction(testData.field());
    }

    @Test(dependsOnMethods = "firstTest")
    public void secondTest() {
        Assert.assertTrue(nextPage.isAt());
    }
}

## 7. STRICT DO NOT DO LIST
- NEVER call setDriver() in test class
- NEVER call driver.quit() in test class
- NEVER use driver.findElement() anywhere in test class
- NEVER use Thread.sleep() anywhere
- NEVER use getter methods on test data (testData.getUsername() is WRONG)
- NEVER add @AfterTest in test class (AbstractTest handles it)
- NEVER instantiate Page Objects outside setup method
- NEVER put navigation (goTo) in setup method — put it in first @Test

## 8. TESTNG XML STRUCTURE
<suite name="Suite Name">
    <test name="Test Name">
        <parameter name="testDataPath" value="test-data/yourdata.json"/>
        <classes>
            <class name="com.omshinde.tests.YourTest"/>
        </classes>
    </test>
</suite>
================================================================
"""