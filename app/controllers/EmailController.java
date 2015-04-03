package controllers;

import java.util.Map;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.data.DynamicForm;
import play.data.Form;
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
     * notifications back on through this endpoint. This cannot be part of the public API, because MailChimp doesn't
     * deal with non-200 status codes. The token that is submitted is set in the configuration, and must match to allow
     * this call to succeed. Subject to change without warning or backwards compatibility.
     * 
     * @return
     * @throws Exception
     */
    public Result unsubscribeFromEmail() throws Exception {
        try {
            String token = getParameter("token");
            if (token == null || !token.equals(bridgeConfig.getEmailUnsubscribeToken())) {
                throw new RuntimeException("Not authorized.");
            }
            // Study has to be provided as an URL parameter:
            String studyId = getParameter("study");
            if (studyId == null) {
                throw new RuntimeException("Study not found.");
            }
            Study study = studyService.getStudy(studyId);

            // MailChimp submits email as data[email]
            String email = getParameter("data[email]");
            if (email == null) {
                email = getParameter("email");
            }
            if (email == null) {
                throw new RuntimeException("Email not found.");
            }
            Account account = accountDao.getAccount(study, email);
            if (account == null) {
                throw new RuntimeException("Account not found.");
            }
            HealthId healthId = healthCodeService.getMapping(account.getHealthId());
            if (healthId == null) {
                throw new RuntimeException("Health code not found.");
            }
            optionsService.setOption(study, healthId.getCode(), ParticipantOption.EMAIL_NOTIFICATIONS, Boolean.FALSE.toString());

            return ok("You have been unsubscribed from future email.");
        } catch(Throwable throwable) {
            return ok(throwable.getMessage());
        }
    }


    private String getParameter(String paramName) {
        Map<String, String[]> parameters = request().queryString();
        String[] values = parameters.get(paramName);
        String param = (values != null && values.length > 0) ? values[0] : null;
        if (param == null) {
            DynamicForm requestData = Form.form().bindFromRequest();
            param = requestData.get("data[email]");
            if (param == null) {
                param = requestData.get("email");
            }
        }
        return param;
    }

}
