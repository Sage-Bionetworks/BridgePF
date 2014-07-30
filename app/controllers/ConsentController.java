package controllers;

import org.sagebionetworks.bridge.models.UserSession;

import play.mvc.Result;

public class ConsentController extends BaseController {

    public Result signUp() throws Exception {
        UserSession session = getSession();
        return jsonResult(session.getSessionToken());
    }

    public Result withdraw() throws Exception {
        UserSession session = getSession();
        return jsonResult(session.getSessionToken());
    }

    public Result send() throws Exception {
        UserSession session = getSession();
        return jsonResult(session.getSessionToken());
    }
}
