package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.ExportService;

/** Controller that interfaces Bridge Server with Bridge Exporter. */
@Controller
public class ExportController extends BaseController {
    private ExportService exportService;

    /** Service for exports. */
    @Autowired
    public final void setExportService(ExportService exportService) {
        this.exportService = exportService;
    }

    /** Kicks off an on-demand export for the given study. */
    public Result startOnDemandExport() throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        exportService.startOnDemandExport(studyId);
        return acceptedResult("Request submitted.");
    }
}
