package controllers;

import java.util.List;

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

    /**
     * Play controller for POST /worker/v2/healthdata. This API is used by worker apps to create health data records,
     * generally from unpacking uploads. This API cannot be used to update existing records.
     *
     * @return Play result, containing the unique ID of the record just created
     */
    public Result createRecord() {
        // TODO: Until worker accounts are implemented, this will use admin accounts.
        getAuthenticatedAdminSession();

        HealthDataRecord record = parseJson(request(), HealthDataRecord.class);
        String recordId = healthDataService.createRecord(record);
        return okResult(recordId);
    }

    /**
     * Play controller for DELETE /admin/v2/healthdata/byHealthCode/:healthCode. This API is used by admins to delete
     * health data records for a given health code (user in study).
     *
     * @param healthCode
     *         health code to delete records for
     * @return Play result indicating number of records deleted
     */
    public Result deleteRecordsForHealthCode(String healthCode) {
        // unlike the other APIs, this is a proper admin API
        getAuthenticatedAdminSession();

        int numDeleted = healthDataService.deleteRecordsForHealthCode(healthCode);
        return okResult(String.format("%d records deleted.", numDeleted));
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
}
