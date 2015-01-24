package controllers;

import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.services.UploadService;
import org.sagebionetworks.bridge.services.UploadValidationService;

import org.springframework.beans.factory.annotation.Autowired;
import play.mvc.Result;

public class UploadController extends BaseController {

    private UploadService uploadService;
    private UploadValidationService uploadValidationService;

    public void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Autowired
    public void setUploadValidationService(UploadValidationService uploadValidationService) {
        this.uploadValidationService = uploadValidationService;
    }

    public Result upload() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        UploadRequest uploadRequest = UploadRequest.fromJson(requestToJSON(request()));
        UploadSession uploadSession = uploadService.createUpload(session.getUser(), uploadRequest);
        return okResult(uploadSession);
    }

    public Result uploadComplete(String uploadId) throws Exception {
        getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());

        // mark upload as complete
        Upload upload = uploadService.getUpload(uploadId);
        uploadService.uploadComplete(upload);

        // kick off upload validation
        uploadValidationService.validateUpload(study, upload);

        return ok("Upload " + uploadId + " complete!");
    }

    // TODO: add API for get validation status and messages
}
