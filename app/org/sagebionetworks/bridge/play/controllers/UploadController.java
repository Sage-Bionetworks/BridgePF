package org.sagebionetworks.bridge.play.controllers;

import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.services.UploadService;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class UploadController extends BaseController {

    private UploadService uploadService;

    @Autowired
    public void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** Gets validation status and messages for the given upload ID. */
    public Result getValidationStatus(String uploadId) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        UploadValidationStatus validationStatus = uploadService.getUploadValidationStatus(
                session.getParticipant(), uploadId);

        // Upload validation status may contain the health data record. Use the filter to filter out health code.
        return ok(HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(validationStatus));
    }

    public Result upload() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        UploadRequest uploadRequest = UploadRequest.fromJson(requestToJSON(request()));
        UploadSession uploadSession = uploadService.createUpload(session.getStudyIdentifier(), session.getParticipant(),
                uploadRequest);
        final Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setUploadSize(uploadRequest.getContentLength());
            metrics.setUploadId(uploadSession.getId());
        }
        return okResult(uploadSession);
    }

    /**
     * Signals to the Bridge server that the upload is complete. This kicks off the asynchronous validation process
     * through the Upload Validation Service.
     */
    public Result uploadComplete(String uploadId) throws Exception {

        final Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setUploadId(uploadId);
        }

        UserSession session = getAuthenticatedAndConsentedSession();

        // mark upload as complete
        Upload upload = uploadService.getUpload(session.getParticipant(), uploadId);
        uploadService.uploadComplete(session.getStudyIdentifier(), upload);

        return okResult("Upload " + uploadId + " complete!");
    }

}
