package controllers;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;

@org.springframework.stereotype.Controller
public abstract class AdminController extends BaseController {

    /**
     * Checks if the user is in the "admin" group.
     */
    protected User checkForAdmin() {
        UserSession session = checkForSession();
        if (session == null || !session.isAuthenticated()) {
            throw new BridgeServiceException("Not signed in.", UNAUTHORIZED);
        }
        User user = session.getUser();
        if (user == null || !user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to add consent document.", FORBIDDEN);
        }
        return user;
    }
}
