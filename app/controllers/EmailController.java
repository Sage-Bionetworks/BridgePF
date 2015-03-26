package controllers;

import java.util.Map;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.springframework.beans.factory.annotation.Autowired;

import play.mvc.Result;

public class EmailController extends BaseController {

    protected AccountDao accountDao;
    
    protected ParticipantOptionsService optionsService;
    
    protected HealthCodeService healthCodeService;
    
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
     * An URL to which a POST can be sent to set the user's email notification preference 
     * to "off". Do not need to be authenticated to call this endpoint. Cannot turn email 
     * notifications back on through this endpoint. Not considered a public part of the API
     * at this time, although publicly accessible. Subject to change without warning or 
     * backwards compatability.
     * @return
     * @throws Exception
     */
    public Result unsubscribeFromEmail() throws Exception {
        // Standards for submitting to this URL are fluid. We should accept a well-formed JSON payload if 
        // we publicize this endpoint. Right now our concern is to receive data from MailChimp that
        // looks like an HTTP form post, and contains this information:

        // Study has to be provided as an URL parameter:
        String studyId = getParameter("study");
        if (studyId == null) {
            throw new EntityNotFoundException(Study.class);
        }
        Study study = studyService.getStudy(studyId);
        
        // MailChimp submits email as data[email]
        String email = getParameter("data[email]");
        if (email == null) {
            throw new EntityNotFoundException(User.class);
        }
        
        Account account = accountDao.getAccount(study, email);
        HealthId healthId = healthCodeService.getMapping(account.getHealthId());
        if (healthId == null) {
            throw new EntityNotFoundException(User.class);
        }
        
        optionsService.setOption(study, healthId.getCode(), ParticipantOption.EMAIL_NOTIFICATIONS, "off");
        return ok();
    }
    
    // No this doesn't appear to be in Play's API
    private String getParameter(String paramName) {
        Map<String,String[]> parameters = request().queryString();
        String[] values = parameters.get(paramName);
        return (values != null && values.length > 0) ? values[0] : null;
    }
    
}
