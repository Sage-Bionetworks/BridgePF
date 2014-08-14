package controllers;

import global.JsonSchemaValidator;

import java.util.Date;
import java.util.List;

import models.IdHolder;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordImpl;
import org.sagebionetworks.bridge.services.HealthDataService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import play.libs.Json;
import play.mvc.Result;

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
        for (int i=0; i < node.size(); i++) {
            JsonNode child = node.get(i);
            validator.validate(tracker, child);
            records.add(HealthDataRecordImpl.fromJson(child));
        }
        
        HealthDataKey key = new HealthDataKey(study, tracker, session.getSessionToken());
        
        List<String> ids = healthDataService.appendHealthData(key, records);
        return ok(Json.toJson(new IdHolder(ids)));
    }
    
    public Result getHealthData(Long trackerId, Long startDate, Long endDate) throws Exception {
        if (startDate == null && endDate == null) {
            return getAllHealthData(trackerId);
        }
        if (startDate == null) {
            startDate = -Long.MAX_VALUE;
        } else if (endDate == null) {
            endDate = Long.MAX_VALUE;
        }
        return getHealthDataByDateRange(trackerId, startDate, endDate);
    }
    
    private Result getAllHealthData(Long trackerId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        
        HealthDataKey key = new HealthDataKey(study, tracker, session.getSessionToken());
        
        List<HealthDataRecord> entries = healthDataService.getAllHealthData(key);
        return ok(Json.toJson(entries));
    }
    
    private Result getHealthDataByDateRange(Long trackerId, Long startDate, Long endDate) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getSessionToken());

        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, new Date(startDate), new Date(endDate));
        return ok(Json.toJson(entries));
    }
    
    public Result getHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getSessionToken());
        
        HealthDataRecord record = healthDataService.getHealthDataRecord(key, recordId);
        return ok(Json.toJson(record));
    }
    
    public Result updateHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getSessionToken());
        
        JsonNode node = requestToJSON(request());

        new JsonSchemaValidator().validate(tracker, node);
        HealthDataRecord record = HealthDataRecordImpl.fromJson(node);
        
        healthDataService.updateHealthDataRecord(key, record);
        return jsonResult("Record updated.");
    }
    
    public Result deleteHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study, tracker, session.getSessionToken());

        healthDataService.deleteHealthDataRecord(key, recordId);
        return jsonResult("Record deleted.");
    }
    
}
