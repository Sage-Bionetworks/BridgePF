package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;

import java.util.Map;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.data.DynamicForm;
import play.data.Form;
import play.mvc.Result;

@Controller
public class EmailController extends BaseController {
    private final Logger LOG = LoggerFactory.getLogger(EmailController.class);

    private AccountDao accountDao;
    private HealthCodeService healthCodeService;

    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
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
            optionsService.setBoolean(study, healthId.getCode(), EMAIL_NOTIFICATIONS, false);

            return ok("You have been unsubscribed from future email.");
        } catch(Throwable throwable) {
            String errorMsg = "Unknown error";
            if (StringUtils.isNotBlank(throwable.getMessage())) {
                errorMsg = throwable.getMessage();
            }
            LOG.error("Error unsubscribing: " + errorMsg, throwable);
            return ok(errorMsg);
        }
    }

    /**
     * No idea how you're supposed to test all this static PF stuff. Will use a spy
     * to work around it.
     */
    protected DynamicForm getPostData() {
        return Form.form().bindFromRequest();
    }

    private String getParameter(String paramName) {
        Map<String, String[]> parameters = request().queryString();
        String[] values = parameters.get(paramName);
        String param = (values != null && values.length > 0) ? values[0] : null;
        if (param == null) {
            // How are you supposed to test crap like this?
            DynamicForm requestData = getPostData();
            param = requestData.get("data[email]");
            if (param == null) {
                param = requestData.get("email");
            }
        }
        return param;
    }

}
