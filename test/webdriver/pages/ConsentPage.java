package webdriver.pages;

import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.CONSENT_TEST_URL;

import org.fluentlenium.core.domain.FluentWebElement;
import play.test.TestBrowser;

public class ConsentPage extends BasePage {

    public ConsentPage(TestBrowser browser) {
        super(browser);
        browser.goTo(CONSENT_TEST_URL);
        assertTrue("Title includes phrase 'Consent to Participate in Bridge'",
                browser.pageSource().contains("Consent to Participate in Bridge"));
    }
    
    public WelcomeScreen getWelcomeScreen() {
        return new WelcomeScreen(browser); 
    }
    
    private class BaseScreen {
        protected String id;
        protected TestBrowser browser;
        protected BaseScreen(String id, TestBrowser browser) {
            this.id = id;
            this.browser = browser;
        }
        protected FluentWebElement getLearnMoreButton() {
            String selector = id+" a[ng-click*='learnMore']";
            waitUntilDisplayed(selector);
            return browser.findFirst(selector);
        }
        protected FluentWebElement getNextLink() {
            return get("nextStep");
        }
        protected FluentWebElement getNextLinkIfDesktop() {
            return get("nextStepIfDesktop");
        }
        protected FluentWebElement getNextLinkIfAnimDone() {
            waitUntilDisplayed(id+" .action"); // wait until this is present
            return get("nextStepIfAnimDone");
        }
        private FluentWebElement get(String name) {
            waitUntilPresent(id+" *[ng-click='"+name+"()']");
            return browser.findFirst(id+" *[ng-click='"+name+"()']");
        }
        protected void click(String cssSelector) {
            waitUntilPresent(cssSelector);
            browser.findFirst(cssSelector).click();
        }
    }
    
    public class WelcomeScreen extends BaseScreen {
        public WelcomeScreen(TestBrowser browser) {
            super("#welcome", browser);
        }
        public TasksScreen getTasksScreen() {
            getNextLink().click();
            waitUntilDisplayed("#tasks");
            return new TasksScreen(browser);
        }
    }
    
    public class TasksScreen extends BaseScreen {
        public TasksScreen(TestBrowser browser) {
            super("#tasks", browser);
        }
        public SensorsScreen getSensorsScreen() {
            click("#surveyImg");
            click("#talkingImg");
            click("#tappingImg");
            click("#walkingImg");
            waitUntilDisplayed("#sensorsDesktop");
            return new SensorsScreen(browser);
        }
    }
    
    public class SensorsScreen extends BaseScreen {
        public SensorsScreen(TestBrowser browser) {
            super("#sensorsDesktop", browser);
        }
        public DeidentificationScreen getDeidentificationScreen() {
            getNextLink().click();
            waitUntilDisplayed("#deidentification");
            return new DeidentificationScreen(browser);
        }
    }
    
    public class DeidentificationScreen extends BaseScreen {
        public DeidentificationScreen(TestBrowser browser) {
            super("#deidentification", browser);
        }
        public AggregationScreen getAggregationScreen() {
            getNextLinkIfAnimDone().click();
            waitUntilDisplayed("#aggregation");
            return new AggregationScreen(browser);
        }
    }
    
    public class AggregationScreen extends BaseScreen {
        public AggregationScreen(TestBrowser browser) {
            super("#aggregation", browser);
        }
        public ImpactScreen getImpactScreen() {
            getNextLinkIfAnimDone().click();
            waitUntilDisplayed("#impact");
            return new ImpactScreen(browser);
        }
    }
    
    public class ImpactScreen extends BaseScreen {
        public ImpactScreen(TestBrowser browser) {
            super("#impact", browser);
        }
        public RiskScreen getRiskScreen() {
            getNextLink().click();
            waitUntilDisplayed("#risk");
            return new RiskScreen(browser);
        }
    }
    
    public class RiskScreen extends BaseScreen {
        public RiskScreen(TestBrowser browser) {
            super("#risk", browser);
        }
        public Risk2Screen getRisk2Screen() {
            getNextLinkIfAnimDone().click();
            waitUntilDisplayed("#risk2");
            return new Risk2Screen(browser);
        }
    }
    
    public class Risk2Screen extends BaseScreen {
        public Risk2Screen(TestBrowser browser) {
            super("#risk2", browser);
        }
        public WithdrawalScreen getWithdrawalScreen() {
            getNextLinkIfAnimDone().click();
            waitUntilDisplayed("#withdrawal");
            return new WithdrawalScreen(browser);
        }
    }
    
    public class WithdrawalScreen extends BaseScreen {
        public WithdrawalScreen(TestBrowser browser) {
            super("#withdrawal", browser);
        }
        public ConsentScreen getConsentScreen() {
            getNextLink().click();
            waitUntilDisplayed("#consent");
            return new ConsentScreen(browser);
        }
    }
    
    public class ConsentScreen extends BaseScreen {
        public ConsentScreen(TestBrowser browser) {
            super("#consent", browser);
        }
        public void viewCompleteAgreement() {
            getLearnMoreButton().click();
        }
        public void disagreeToConsent() {
            getDeclineLink().click();
            assertTrue("Message popup notes you must consent",
                    messagePopup().getText().contains("You must give consent to participate in this study."));
        }
        public void enterEmptyConsent() {
            
        }
        public void enterInvalidConsent() {
            
        }
        public void enterValidConsent() {
            
        }
        /*
        Agree should be disabled
        Enter name, still disabled
        Enter crap date, still disabled
        Enter valid date
        Agree is enabled
        I don't agree pops up the message
         */
        
        private FluentWebElement getDeclineLink() {
            String selector = id+" a[ng-click*='decline']";
            waitUntilDisplayed(selector);
            return browser.findFirst(selector);
        }
        
        public ThankYouScreen getThankYouScreen() {
            getNextLink().click();
            waitUntilDisplayed("#thankyou");
            return new ThankYouScreen(browser);
        }
    }
    
    public class ThankYouScreen extends BaseScreen {
        public ThankYouScreen(TestBrowser browser) {
            super("#thankyou", browser);
        }
    }

}
