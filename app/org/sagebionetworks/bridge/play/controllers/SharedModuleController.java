package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleImportStatus;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.SharedModuleService;

/** Play controller for importing shared modules. */
@Controller
public class SharedModuleController extends BaseController {
    private SharedModuleService moduleService;

    /** Shared Module Service, configured by Spring. */
    @Autowired
    public void setModuleService(SharedModuleService moduleService) {
        this.moduleService = moduleService;
    }

    /** Imports a specific module version into the current study. */
    public Result importModuleByIdAndVersion(String moduleId, int moduleVersion) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        SharedModuleImportStatus status = moduleService.importModuleByIdAndVersion(studyId, moduleId, moduleVersion);
        return okResult(status);
    }

    /** Imports the latest published version of a module into the current study. */
    public Result importModuleByIdLatestPublishedVersion(String moduleId) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        SharedModuleImportStatus status = moduleService.importModuleByIdLatestPublishedVersion(studyId, moduleId);
        return okResult(status);
    }
}
