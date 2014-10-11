package controllers;

import global.JsonSchemaValidator;

import java.util.List;

import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.IdVersionHolder;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.HealthDataService;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class HealthDataController extends BaseController {

    private HealthDataService healthDataService;

    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    public Result appendHealthData(Long trackerId) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerById(trackerId);
        JsonSchemaValidator validator = new JsonSchemaValidator();

        JsonNode node = requestToJSON(request());

        List<HealthDataRecord> records = Lists.newArrayListWithCapacity(node.size());
        for (int i = 0; i < node.size(); i++) {
            JsonNode child = node.get(i);
            validator.validate(tracker, child);
            records.add(DynamoHealthDataRecord.fromJson(child));
        }

        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        List<IdVersionHolder> ids = healthDataService.appendHealthData(key, records);
        return okResult(ids);
    }

    public Result getHealthData(Long trackerId, String startDate, String endDate) throws Exception {
        if (startDate == null && endDate == null) {
            return getAllHealthData(trackerId);
        }
        Long start, end;
        if (startDate == null) {
            start = -Long.MAX_VALUE;
        } else {
            start = DateUtils.convertToMillisFromEpoch(startDate);
        }
        if (endDate == null) {
            end = Long.MAX_VALUE;
        } else {
            end = DateUtils.convertToMillisFromEpoch(endDate);
        }
        return getHealthDataByDateRange(trackerId, start, end);
    }

    private Result getAllHealthData(Long trackerId) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerById(trackerId);

        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        List<HealthDataRecord> entries = healthDataService.getAllHealthData(key);
        return okResult(entries);
    }

    private Result getHealthDataByDateRange(Long trackerId, long startDate, long endDate) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, startDate, endDate);
        return okResult(entries);
    }

    public Result getHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        HealthDataRecord record = healthDataService.getHealthDataRecord(key, recordId);
        return okResult(record);
    }

    public Result updateHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        JsonNode node = requestToJSON(request());
        new JsonSchemaValidator().validate(tracker, node);
        HealthDataRecord record = DynamoHealthDataRecord.fromJson(node);

        IdVersionHolder holder = healthDataService.updateHealthDataRecord(key, record);
        return okResult(holder);
    }

    public Result deleteHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        healthDataService.deleteHealthDataRecord(key, recordId);
        return okResult("Record deleted.");
    }

}
