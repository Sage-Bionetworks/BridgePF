package controllers;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.BackfillService;

import play.mvc.Result;

public class BackfillController extends BaseController {

    private BackfillService backfillService;
    private StudyControllerService studyControllerService;

    public void setBackfillService(BackfillService backfillService) {
        this.backfillService = backfillService;
    }
    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public Result stormpathUserConsent() throws Exception {
        checkUser();
        Study study = studyControllerService.getStudyByHostname(request());
        int total = backfillService.stormpathUserConsent(study);
        return okResult("Done. " + total + " accounts backfilled.");
    }

    public Result dynamoUserConsent() throws Exception {
        checkUser();
        int total = backfillService.dynamoUserConsent();
        return okResult("Done. " + total + " records backfilled.");
    }

    private void checkUser() throws Exception {
        UserSession session = getSession();
        User user = session.getUser();
        if (!user.getRoles().contains("backfill")) {
            throw new BridgeServiceException(user.getUsername() + " not allowed to perform backfill.", 403);
        }
    }
}
