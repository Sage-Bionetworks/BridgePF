package controllers;

import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
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

    /** Service handler for upload validation. This is configured by Spring. */
    @Autowired
    public void setUploadValidationService(UploadValidationService uploadValidationService) {
        this.uploadValidationService = uploadValidationService;
    }

    /** Gets validation status and messages for the given upload ID. */
    public Result getValidationStatus(String uploadId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        Upload upload = uploadService.getUpload(session.getUser(), uploadId);
        UploadValidationStatus validationStatus = UploadValidationStatus.from(upload);
        return okResult(validationStatus);
    }

    public Result upload() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        UploadRequest uploadRequest = UploadRequest.fromJson(requestToJSON(request()));
        UploadSession uploadSession = uploadService.createUpload(session.getUser(), uploadRequest);
        return okResult(uploadSession);
    }

    /**
     * Signals to the Bridge server that the upload is complete. This kicks off the asynchronous validation process
     * through the Upload Validation Service.
     */
    public Result uploadComplete(String uploadId) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();

        // mark upload as complete
        Upload upload = uploadService.getUpload(session.getUser(), uploadId);
        uploadService.uploadComplete(upload);

        // kick off upload validation
        Study study = studyService.getStudy(session.getStudyIdentifier());
        uploadValidationService.validateUpload(study, upload);

        return ok("Upload " + uploadId + " complete!");
    }
}
