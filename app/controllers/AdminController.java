package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.UserSession;

@org.springframework.stereotype.Controller
public abstract class AdminController extends BaseController {

    /**
     * Checks if the user is in the "admin" group.
     */
    protected UserSession getAuthenticatedAdminSession() throws BridgeServiceException {
        UserSession session = getAuthenticatedSession();
        if (!session.getUser().isInRole(BridgeConstants.ADMIN_GROUP)) {
            throw new UnauthorizedException();
        }
        return session;
    }
}
