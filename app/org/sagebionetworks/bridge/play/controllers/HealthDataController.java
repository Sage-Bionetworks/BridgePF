package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import java.io.IOException;
import java.util.List;

import static org.sagebionetworks.bridge.Roles.WORKER;

@Controller
public class HealthDataController extends BaseController {

    private HealthDataService healthDataService;

    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    /**
     * API to allow consented users to submit health data in a synchronous API, instead of using the asynchronous
     * upload API. This is most beneficial for small data sets, like simple surveys. This API returns the health data
     * record produced from this submission, which includes the record ID.
     */
    public Result submitHealthData() throws JsonProcessingException, IOException {
        // Submit health data.
        UserSession session = getAuthenticatedAndConsentedSession();
        HealthDataSubmission healthDataSubmission = parseJson(request(), HealthDataSubmission.class);
        HealthDataRecord savedRecord = healthDataService.submitHealthData(session.getStudyIdentifier(),
                session.getParticipant(), healthDataSubmission);

        // Write record ID into the metrics, for logging and diagnostics.
        Metrics metrics = getMetrics();
        if (metrics != null) {
            metrics.setRecordId(savedRecord.getId());
        }

        // Record upload time to user's request info. This allows us to track the last time the user submitted.
        RequestInfo requestInfo = getRequestInfoBuilder(session).withUploadedOn(DateUtils.getCurrentDateTime())
                .build();
        cacheProvider.updateRequestInfo(requestInfo);

        // Return the record produced by this submission. Filter out Health Code, of course.
        return createdResult(HealthDataRecord.PUBLIC_RECORD_WRITER, savedRecord);
    }

    public Result updateRecordsStatus() throws JsonProcessingException{
        getAuthenticatedSession(WORKER);

        RecordExportStatusRequest recordExportStatusRequest = parseJson(request(), RecordExportStatusRequest.class);

        List<String> updatedRecordIds = healthDataService.updateRecordsWithExporterStatus(recordExportStatusRequest);

        return okResult("Update exporter status to: " + updatedRecordIds + " complete.");
    }
}
