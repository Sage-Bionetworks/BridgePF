package controllers;

import java.util.Map;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller("emailController")
public class EmailController extends BaseController {

    private AccountDao accountDao;

    private ParticipantOptionsService optionsService;

    private HealthCodeService healthCodeService;

    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    public void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @Autowired
    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    /**
     * An URL to which a POST can be sent to set the user's email notification preference to "off". Cannot turn email
     * notifications back on through this endpoint. Not considered a public part of the API at this time, although
     * publicly accessible. The token that is submitted is set in the configuration, and must match to allow this 
     * call to succeed. Subject to change without warning or backwards compatibility.
     * 
     * @return
     * @throws Exception
     */
    public Result unsubscribeFromEmail() throws Exception {
        String token = getParameter("token");
        if (token == null || !token.equals(bridgeConfig.getEmailUnsubscribeToken())) {
            return ok("Not authorized.\n").as("text/plain");
        }
        // Study has to be provided as an URL parameter:
        String studyId = getParameter("study");
        if (studyId == null) {
            return ok("Study not found.\n").as("text/plain");
        }
        Study study = null;
        try {
            study = studyService.getStudy(studyId);
        } catch(EntityNotFoundException e) {
            return ok("Study not found.\n").as("text/plain");
        }
        // MailChimp submits email as data[email]
        String email = getParameter("data[email]");
        if (email == null) {
            email = getParameter("email");
        }
        if (email == null) {
            return ok("Email not found.\n").as("text/plain");
        }
        Account account = accountDao.getAccount(study, email);
        if (account == null) {
            return ok("Account not found.\n").as("text/plain");
        }
        HealthId healthId = healthCodeService.getMapping(account.getHealthId());
        if (healthId == null) {
            return ok("Health code not found.\n").as("text/plain");
        }
        optionsService.setOption(study, healthId.getCode(), ParticipantOption.EMAIL_NOTIFICATIONS, Boolean.FALSE.toString());

        return ok("You have been unsubscribed from future email.\n").as("text/plain");
    }

    // This doesn't appear to be in Play's API
    private String getParameter(String paramName) {
        Map<String, String[]> parameters = request().queryString();
        String[] values = parameters.get(paramName);
        return (values != null && values.length > 0) ? values[0] : null;
    }

}
