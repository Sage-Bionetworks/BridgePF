package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;

import java.util.Map;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.studies.Study;

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

    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
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
            // Token has to be provided as an URL parameter
            String token = getParameter("token");
            if (token == null || !token.equals(bridgeConfig.getEmailUnsubscribeToken())) {
                throw new RuntimeException("Not authorized.");
            }
            // Study has to be provided as an URL parameter
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
            
            // This should always return a healthCode
            String healthCode = accountDao.getHealthCodeForEmail(study, email);
            optionsService.setBoolean(study, healthCode, EMAIL_NOTIFICATIONS, false);
            
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

    protected DynamicForm getPostData() {
        return Form.form().bindFromRequest();
    }
    
    protected Map<String, String[]> getParams() {
        return request().queryString();
    }

    // Checks both query string parameters and form data for the required values.
    private String getParameter(String paramName) {
        String[] values = getParams().get(paramName);
        if (values != null && values.length > 0) {
            return values[0];
        }
        DynamicForm postData = getPostData();
        return postData.get(paramName);
    }

}
