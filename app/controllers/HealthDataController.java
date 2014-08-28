package controllers;

import global.JsonSchemaValidator;

import java.util.List;

import org.sagebionetworks.bridge.models.Date;
import org.sagebionetworks.bridge.models.IdVersionHolder;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordImpl;
import org.sagebionetworks.bridge.services.HealthDataService;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class HealthDataController extends BaseController {

    private HealthDataService healthDataService;
    private StudyControllerService studyControllerService;

    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }

    public void setStudyControllerService(StudyControllerService scs) {
        this.studyControllerService = scs;
    }

    public Result appendHealthData(Long trackerId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        JsonSchemaValidator validator = new JsonSchemaValidator();

        JsonNode node = requestToJSON(request());

        List<HealthDataRecord> records = Lists.newArrayListWithCapacity(node.size());
        for (int i = 0; i < node.size(); i++) {
            JsonNode child = node.get(i);
            validator.validate(tracker, child);
            records.add(HealthDataRecordImpl.fromJson(child));
        }

        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        List<IdVersionHolder> ids = healthDataService.appendHealthData(key, records);
        return ok(constructJSON(ids));
    }

    public Result getHealthData(Long trackerId, String startDate, String endDate) throws Exception {
        if (startDate == null && endDate == null) {
            return getAllHealthData(trackerId);
        }
        Date start, end;
        if (startDate == null) {
            start = new Date(-Long.MAX_VALUE);
        } else {
            start = new Date(startDate);
        }
        if (endDate == null) {
            end = new Date(Long.MAX_VALUE);
        } else {
            end = new Date(endDate);
        }
        return getHealthDataByDateRange(trackerId, start, end);
    }

    private Result getAllHealthData(Long trackerId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);

        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        List<HealthDataRecord> entries = healthDataService.getAllHealthData(key);
        return ok(constructJSON(entries));
    }

    private Result getHealthDataByDateRange(Long trackerId, Date startDate, Date endDate) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, startDate, endDate);
        return ok(constructJSON(entries));
    }

    public Result getHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        HealthDataRecord record = healthDataService.getHealthDataRecord(key, recordId);
        return ok(constructJSON(record));
    }

    public Result updateHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        JsonNode node = requestToJSON(request());

        new JsonSchemaValidator().validate(tracker, node);
        HealthDataRecord record = HealthDataRecordImpl.fromJson(node);

        IdVersionHolder holder = healthDataService.updateHealthDataRecord(key, record);
        return ok(constructJSON(holder));
    }

    public Result deleteHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getUser());

        healthDataService.deleteHealthDataRecord(key, recordId);
        return okResult("Record deleted.");
    }

}
