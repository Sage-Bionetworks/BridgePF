package controllers;

import org.sagebionetworks.bridge.models.UserSession;

import play.mvc.Result;

public class ConsentController extends BaseController {

    public Result signUp() throws Exception {
        // TODO: Implement with ConsentService
        UserSession session = getSession();
        return ok(Boolean.toString(session.isConsent()));
    }

    public Result withdraw() throws Exception {
        // TODO: Implement with ConsentService
        UserSession session = getSession();
        return ok(Boolean.toString(session.isConsent()));
    }

    public Result sendCopy() throws Exception {
        // TODO: Implement with ConsentService
        UserSession session = getSession();
        return ok(Boolean.toString(session.isConsent()));
    }
}
