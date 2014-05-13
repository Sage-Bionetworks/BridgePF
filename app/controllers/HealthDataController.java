package controllers;

import global.JsonSchemaValidator;

import java.util.Date;
import java.util.List;

import models.IdHolder;
import models.JsonPayload;
import models.UserSession;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordImpl;
import org.sagebionetworks.bridge.services.HealthDataService;

import com.fasterxml.jackson.databind.JsonNode;

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
        
        JsonNode node = request().body().asJson();
        
        new JsonSchemaValidator().validate(tracker, node);
        
        HealthDataRecord record = HealthDataRecordImpl.fromJson(node);
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        String id = healthDataService.appendHealthData(key, record);
        return jsonResult(new JsonPayload<IdHolder>(new IdHolder(id)));
    }

    public Result getAllHealthData(Long trackerId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        List<HealthDataRecord> entries = healthDataService.getAllHealthData(key);
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(tracker.getType() + "[]", entries));
    }
    
    public Result getHealthDataByDate(Long trackerId, Long date) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, new Date(date), new Date(date));
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(HealthDataRecord.class.getName(), entries));
    }
    
    public Result getHealthDataByDateRange(Long trackerId, Long startDate, Long endDate) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, new Date(startDate), new Date(endDate));
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(HealthDataRecord.class.getName(), entries));
    }
    
    public Result getHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        HealthDataRecord record = healthDataService.getHealthDataRecord(key, recordId);
        return jsonResult(new JsonPayload<HealthDataRecord>(HealthDataRecord.class.getName(), record));
    }
    
    public Result updateHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        JsonNode node = request().body().asJson();
        new JsonSchemaValidator().validate(tracker, node);
        HealthDataRecord record = HealthDataRecordImpl.fromJson(node);
        
        healthDataService.updateHealthDataRecord(key, record);
        return jsonResult("Record updated.");
    }
    
    public Result deleteHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        healthDataService.deleteHealthDataRecord(key, recordId);
        return jsonResult("Record deleted.");
    }
    
}
