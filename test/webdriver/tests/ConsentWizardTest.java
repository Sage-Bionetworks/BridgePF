package webdriver.tests;

import org.junit.Ignore;
import org.junit.Test;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.ConsentPage;

public class ConsentWizardTest extends BaseIntegrationTest {

    @Test
    public void canProgressThroughWizard() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                ConsentPage page = new ConsentPage(browser);
                
                ConsentPage.WelcomeScreen welcomeScreen = page.getWelcomeScreen();
                
                ConsentPage.TasksScreen tasksScreen = welcomeScreen.getTasksScreen();
                
                ConsentPage.SensorsScreen sensorsScreen = tasksScreen.getSensorsScreen();
                
                ConsentPage.DeidentificationScreen deidentificationScreen = sensorsScreen.getDeidentificationScreen();
                
                ConsentPage.AggregationScreen aggregationScreen = deidentificationScreen.getAggregationScreen();
                
                ConsentPage.ImpactScreen impactScreen = aggregationScreen.getImpactScreen();
                
                ConsentPage.RiskScreen riskScreen = impactScreen.getRiskScreen();
                
                ConsentPage.Risk2Screen risk2Screen = riskScreen.getRisk2Screen();
                
                ConsentPage.WithdrawalScreen withdrawalScreen = risk2Screen.getWithdrawalScreen();
                
                ConsentPage.ConsentScreen consentScreen = withdrawalScreen.getConsentScreen();
                
                // These are not asserting anything, however.
                consentScreen.viewCompleteAgreement();
                consentScreen.disagreeToConsent();
                
                ConsentPage.ThankYouScreen thankYouScreen = consentScreen.getThankYouScreen();
            }
        });
    }
    
    @Test
    @Ignore
    public void allLearnMoreButtonsWork() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                ConsentPage page = new ConsentPage(browser);
            }
        });
    }

}
