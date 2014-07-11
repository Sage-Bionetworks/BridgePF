package webdriver.tests;

import org.junit.Test;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;
import webdriver.pages.JoinPage;

public class SignUpTest extends BaseIntegrationTest {

    @Test
    public void validSignUp() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();

                join.enterValidData();
            }
        });
    }

    @Test
    public void signUpRejectsInvalidEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();
                join.enterInvalidData("bridge", "bridgeit", "P4ssword",
                        "P4ssword");
                join.assertEmailEmailError();
            }
        });
    }

    @Test
    public void signUpRequiresEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();
                join.enterValidData();
                join.enterInvalidDataAfterValidData("bridge", "", "P4ssword",
                        "P4ssword");
                join.assertEmailRequiredError();
            }
        });
    }

    @Test
    public void signUpRejectsMismatchedPasswords() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();
                join.enterInvalidData("bridge", "bridgeit@sagebase.org",
                        "P4ssword", "P4ssword2");
                join.assertPasswordConfirmEqualError();
            }
        });
    }

    @Test
    public void signUpRejectsMissingUsername() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();
                join.enterInvalidData("", "bridgeit@sagebase.org", "P4ssword",
                        "P4ssword");
            }
        });
    }

    @Test
    public void signUpRejectsMissingEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();
                join.enterInvalidData("bridge", "", "P4ssword", "P4ssword");
            }
        });
    }

    @Test
    public void signUpRejectsMissingPassword() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();
                join.enterInvalidData("bridge", "bridgeit@sagebase.org", "",
                        "P4ssword");
            }
        });
    }

    @Test
    public void signUpRejectsMissingPasswordConfirmation() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                JoinPage join = page.getJoinPage();
                join.enterInvalidData("bridge", "bridgeit@sagebase.org",
                        "P4ssword", "");
            }
        });
    }

}
