package org.sagebionetworks.bridge.play.controllers;

import java.io.IOException;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.UploadService;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.BodyParser;
import play.mvc.Result;

@Controller
public class UploadController extends BaseController {

    private UploadService uploadService;
    
    private HealthDataService healthDataService;
    
    private HealthCodeDao healthCodeDao;

    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }
    
    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }
    
    @Autowired
    final void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    /** Gets validation status and messages for the given upload ID. */
    public Result getValidationStatus(String uploadId) throws JsonProcessingException, IOException {
        UserSession session = getSessionEitherConsentedOrInRole(Roles.RESEARCHER);
        
        // If not a researcher, validate that this user owns the upload
        if (!session.isInRole(Roles.RESEARCHER)) {
            Upload upload = uploadService.getUpload(uploadId);
            if (!session.getHealthCode().equals(upload.getHealthCode())) {
                throw new UnauthorizedException();
            }
        }
        
        UploadValidationStatus validationStatus = uploadService.getUploadValidationStatus(uploadId);
        
        // Upload validation status may contain the health data record. Use the filter to filter out health code.
        return okResult(HealthDataRecord.PUBLIC_RECORD_WRITER, validationStatus);
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
        
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withUploadedOn(DateUtils.getCurrentDateTime()).build();
        cacheProvider.updateRequestInfo(requestInfo);
        
        return okResult(uploadSession);
    }

    /**
     * <p>
     * Signals to the Bridge server that the upload is complete. This kicks off the asynchronous validation process
     * through the Upload Validation Service.
     * </p>
     * <p>
     * If synchronous is set to "true", we will wait until upload validation is complete, then return the upload
     * validation status. This is generally recommended only for App Development, as some large uploads might take
     * several seconds to complete.
     * </p>
     * <p>
     * If synchronous is set to anything else, we will return a validation status immediately (which will often be in
     * the "validation_in_progress" state) and let upload validation run in the background.
     * </p>
     */
    @BodyParser.Of(BodyParser.Empty.class)
    public Result uploadComplete(String uploadId, String synchronous) throws Exception {
        final Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setUploadId(uploadId);
        }

        // User can be a worker account (get study and health code from the upload itself)...
        UserSession session = getAuthenticatedSession();
        if (session.isInRole(Roles.WORKER)) {
            
            Upload upload = uploadService.getUpload(uploadId);
            String studyId = healthCodeDao.getStudyIdentifier(upload.getHealthCode());
            uploadService.uploadComplete(new StudyIdentifierImpl(studyId), UploadCompletionClient.S3_WORKER, upload);
        } else {
            // Or, the consented user that originally made the upload request. Check that health codes match.
            // Do not need to look up the study.
            session = getAuthenticatedAndConsentedSession();

            Upload upload = uploadService.getUpload(uploadId);
            if (!session.getHealthCode().equals(upload.getHealthCode())) {
                throw new UnauthorizedException();
            }
            uploadService.uploadComplete(session.getStudyIdentifier(), UploadCompletionClient.APP, upload);
        }

        // Boolean.valueOf() converts "true" (ignoring case) to true, and everything else to false (including null).
        boolean synchronousBool = Boolean.valueOf(synchronous);

        // In async mode, we get the validation status (probably in validation_in_progress) and return immediately.
        // In sync mode, we poll until the validation status is complete (or failed or another non-transient status).
        UploadValidationStatus validationStatus;
        if (synchronousBool) {
            validationStatus = uploadService.pollUploadValidationStatusUntilComplete(uploadId);
        } else {
            validationStatus = uploadService.getUploadValidationStatus(uploadId);
        }

        // Upload validation status may contain the health data record. Use the filter to filter out health code.
        return okResult(HealthDataRecord.PUBLIC_RECORD_WRITER, validationStatus);
    }
    
    public Result getUpload(String uploadId) throws Exception {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER, Roles.RESEARCHER);

        if (uploadId.startsWith("recordId:")) {
            String recordId = uploadId.split(":")[1];

            // This service does not throw an exception if the record is not found
            HealthDataRecord record = healthDataService.getRecordById(recordId);
            if (record == null) {
                throw new EntityNotFoundException(HealthDataRecord.class);
            }
            uploadId = record.getUploadId();
        }
        UploadView uploadView = uploadService.getUploadView(uploadId);

        // Even if the ID is valid, ultimately you cannot view an upload outside of your study
        String studyId = session.getStudyIdentifier().getIdentifier();
        if (!uploadView.getUpload().getStudyId().equals(studyId)) {
            throw new UnauthorizedException();
        }
        // This filters out healthCode
        return okResult(HealthDataRecord.PUBLIC_RECORD_WRITER, uploadView);
    }
}
