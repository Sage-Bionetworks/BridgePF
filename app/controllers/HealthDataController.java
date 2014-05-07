package controllers;

import java.util.Date;
import java.util.List;

import models.IdHolder;
import models.JsonPayload;
import models.UserSession;

import org.sagebionetworks.bridge.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.healthdata.HealthDataRecordImpl;
import org.sagebionetworks.bridge.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.services.HealthDataService;

import com.fasterxml.jackson.databind.JsonNode;

import play.Logger;
import play.mvc.Result;

public class HealthDataController extends BaseController {

    private HealthDataService healthDataService;
    
    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }
    
    public Result appendHealthData(Long studyId, Long trackerId) throws Exception {
        UserSession session = getSession();
        
        JsonNode node = request().body().asJson();
        HealthDataRecord record = HealthDataRecordImpl.fromJson(node);
        
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        String id = healthDataService.appendHealthData(key, record);
        return jsonResult(new JsonPayload<IdHolder>(new IdHolder(id)));
    }

    public Result getAllHealthData(Long studyId, Long trackerId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        
        List<HealthDataRecord> entries = healthDataService.getAllHealthData(key);
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(HealthDataRecord.class.getName(), entries));
    }
    
    public Result getHealthDataByDate(Long studyId, Long trackerId, Long date) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, new Date(date), new Date(date));
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(HealthDataRecord.class.getName(), entries));
    }
    
    public Result getHealthDataByDateRange(Long studyId, Long trackerId, Long startDate, Long endDate) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, new Date(startDate), new Date(endDate));
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(HealthDataRecord.class.getName(), entries));
    }
    
    public Result getHealthDataRecord(Long studyId, Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        HealthDataRecord record = healthDataService.getHealthDataRecord(key, recordId);
        return jsonResult(new JsonPayload<HealthDataRecord>(HealthDataRecord.class.getName(), record));
    }
    
    public Result updateHealthDataRecord(Long studyId, Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        JsonNode node = request().body().asJson();
        HealthDataRecord record = HealthDataRecordImpl.fromJson(node);
        
        healthDataService.updateHealthDataRecord(key, record);
        return jsonResult("Record updated.");
    }
    
    public Result deleteHealthDataRecord(Long studyId, Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        healthDataService.deleteHealthDataRecord(key, recordId);
        return jsonResult("Record deleted.");
    }
    
}
