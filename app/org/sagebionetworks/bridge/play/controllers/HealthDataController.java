package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import java.util.List;

import static org.sagebionetworks.bridge.Roles.WORKER;

@Controller
public class HealthDataController extends BaseController {

    private HealthDataService healthDataService;

    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    public Result updateRecordsStatus() throws JsonProcessingException{
        UserSession session = getAuthenticatedSession(WORKER);

        RecordExportStatusRequest recordExportStatusRequest = parseJson(request(), RecordExportStatusRequest.class);

        List<String> updatedRecordIds = healthDataService.updateRecordsWithExporterStatus(recordExportStatusRequest);

        return okResult("Update exporter status to: " + updatedRecordIds + " complete.");
    }
}
