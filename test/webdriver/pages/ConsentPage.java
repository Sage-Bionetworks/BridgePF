package webdriver.pages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.CONSENT_TEST_URL;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;

import org.fluentlenium.core.domain.FluentWebElement;

import play.test.TestBrowser;

public class ConsentPage extends BasePage {

    public ConsentPage(TestBrowser browser) {
        super(browser);
        browser.goTo(TEST_BASE_URL + CONSENT_TEST_URL);
        assertTrue("Title includes phrase 'Consent to Participate in Bridge'",
                browser.pageSource().contains("Consent to Participate in Bridge"));
    }
    
    public WelcomeScreen getWelcomeScreen() {
        return new WelcomeScreen(browser); 
    }
    
    private class BaseScreen extends BasePage {
        protected String id;
        protected BaseScreen(String id, TestBrowser browser) {
            super(browser);
            this.id = id;
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
        protected FluentWebElement getNextLinkWhenAnimationDone() {
            waitUntilDisplayed(id + " .action");
            return get("nextStepIfAnimDone");
        }
        private FluentWebElement get(String name) {
            waitUntilPresent(id+" *[ng-click='"+name+"()']");
            return browser.findFirst(id+" *[ng-click='"+name+"()']");
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
            getNextLinkWhenAnimationDone().click();
            waitUntilDisplayed("#aggregation");
            return new AggregationScreen(browser);
        }
    }
    
    public class AggregationScreen extends BaseScreen {
        public AggregationScreen(TestBrowser browser) {
            super("#aggregation", browser);
        }
        public ImpactScreen getImpactScreen() {
            getNextLinkWhenAnimationDone().click();
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
            getNextLinkWhenAnimationDone().click();
            waitUntilDisplayed("#risk2");
            return new Risk2Screen(browser);
        }
    }
    
    public class Risk2Screen extends BaseScreen {
        public Risk2Screen(TestBrowser browser) {
            super("#risk2", browser);
        }
        public WithdrawalScreen getWithdrawalScreen() {
            getNextLinkWhenAnimationDone().click();
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
            waitUntilDisplayed("#consentDialog");
            waitUntilDisplayed(".close");
            click(".close");
        }
        public void disagreeToConsent() {
            getDeclineLink().click();
            String msg = messagePopup().getText();
            assertEquals("Message notes you must consent","You must give consent to participate in this study.",msg);
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
