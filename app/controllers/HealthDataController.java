package controllers;

import java.util.List;

import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.HealthDataService;

import org.springframework.beans.factory.annotation.Autowired;
import play.mvc.Result;

/** Play controller for health data APIs. */
public class HealthDataController extends BaseController {
    private HealthDataService healthDataService;

    /** Service handler for health data APIs. This is configured by Spring. */
    @Autowired
    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    /* HEALTH DATA RECORD APIs */

    /**
     * Play controller for POST /worker/v2/healthdata. This API is used by worker apps to create health data records,
     * generally from unpacking uploads.
     *
     * @return Play result, containing the unique ID of the record just created
     */
    public Result createOrUpdateRecord() {
        // TODO: Until worker accounts are implemented, this will use admin accounts.
        getAuthenticatedAdminSession();

        HealthDataRecord record = parseJson(request(), HealthDataRecord.class);
        String recordId = healthDataService.createOrUpdateRecord(record);
        return okResult(recordId);
    }

    /**
     * Play controller for GET /worker/v2/healthdata/byId. This API is used by worker apps to fetch health data records
     * by the specified record ID.
     *
     * @param id
     *         record ID
     * @return Play result containing the health data record
     */
    public Result getRecordById(String id) {
        // TODO: Until worker accounts are implemented, this will use admin accounts.
        getAuthenticatedAdminSession();

        HealthDataRecord record = healthDataService.getRecordById(id);
        return okResult(record);
    }

    /**
     * Play controller for GET /worker/v2/healthdata/byUploadDate/:uploadDate. This API is used by worker apps to
     * query all health data records uploaded for a specific date, generally used for export.
     *
     * @param uploadDate
     *         the upload date in YYYY-MM-DD format
     * @return Play result with the list of records in JSON format
     */
    public Result getRecordsForUploadDate(String uploadDate) throws Exception {
        // TODO: Until worker accounts are implemented, this will use admin accounts.
        getAuthenticatedAdminSession();

        List<HealthDataRecord> recordList = healthDataService.getRecordsForUploadDate(uploadDate);
        return okResult(recordList);
    }

    /* HEALTH DATA ATTACHMENT APIs */

    /**
     * Play controller for POST /worker/v2/healthDataAttachment. This API is used by worker apps to create or update
     * health data attachments, generally from unpacking uploads.
     *
     * @return Play result containing the unique ID of the attachment just created
     */
    public Result createOrUpdateAttachment() {
        // TODO: Until worker accounts are implemented, this will use admin accounts.
        getAuthenticatedAdminSession();

        HealthDataAttachment attachment = parseJson(request(), HealthDataAttachment.class);
        String attachmentId = healthDataService.createOrUpdateAttachment(attachment);
        return okResult(attachmentId);
    }
}
