package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.ASSETS_HOST;

import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class ApplicationController extends BaseController {

    private static final String ASSETS_BUILD = "201501291830";

    public Result loadApp() throws Exception {
        return ok(views.html.index.render());
    }

    public Result verifyEmail(String study) {
        Study studyObj = studyService.getStudy(study);
        return ok(views.html.verifyEmail.render(ASSETS_HOST, ASSETS_BUILD, studyObj.getName(), studyObj.getSupportEmail()));
    }

    public Result resetPassword(String study) {
        Study studyObj = studyService.getStudy(study);
        return ok(views.html.resetPassword.render(ASSETS_HOST, ASSETS_BUILD, studyObj.getName(), studyObj.getSupportEmail()));
    }
}
