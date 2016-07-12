package org.sagebionetworks.bridge.play.controllers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
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
    
    private HealthCodeDao healthCodeDao;

    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }
    
    @Autowired
    final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    /** Gets validation status and messages for the given upload ID. */
    public Result getValidationStatus(String uploadId) throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        // Validate that this user owns the upload
        Upload upload = uploadService.getUpload(uploadId);
        if (!session.getHealthCode().equals(upload.getHealthCode())) {
            throw new UnauthorizedException();
        }
        
        UploadValidationStatus validationStatus = uploadService.getUploadValidationStatus(uploadId);
        
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

        // User can be a worker account (get study and health code from the upload itself)...
        UserSession session = getAuthenticatedSession();
        if (session.isInRole(Roles.WORKER)) {
            
            Upload upload = uploadService.getUpload(uploadId);
            String studyId = healthCodeDao.getStudyIdentifier(upload.getHealthCode());
            uploadService.uploadComplete(new StudyIdentifierImpl(studyId), upload);
            
            return okResult("Upload " + uploadId + " complete!");
        }
        
        // Or, the consented user that originally made the upload request. Check that health codes match.
        // Do not need to look up the study.
        session = getAuthenticatedAndConsentedSession();
        
        Upload upload = uploadService.getUpload(uploadId);
        if (!session.getHealthCode().equals(upload.getHealthCode())) {
            throw new UnauthorizedException();
        }
        uploadService.uploadComplete(session.getStudyIdentifier(), upload);

        return okResult("Upload " + uploadId + " complete!");
    }

}
