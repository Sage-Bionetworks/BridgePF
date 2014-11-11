package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;

public abstract class ResearcherController extends BaseController {

    protected UserSession getAuthenticatedResearcherOrAdminSession(Study study) {
        UserSession session = getAuthenticatedSession();
        User user = session.getUser();
        if (user.isInRole(BridgeConstants.ADMIN_GROUP) || user.isInRole(study.getResearcherRole())) {
            return session;
        }
        throw new UnauthorizedException();
    }
    
}
