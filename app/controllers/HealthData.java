package controllers;

import java.util.Date;
import java.util.List;

import models.IdHolder;
import models.JsonPayload;
import models.UserSession;

import org.sagebionetworks.bridge.healthdata.HealthDataEntry;
import org.sagebionetworks.bridge.healthdata.HealthDataEntryImpl;
import org.sagebionetworks.bridge.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.services.HealthDataService;

import com.fasterxml.jackson.databind.JsonNode;

import play.Logger;
import play.mvc.Result;

public class HealthData extends BaseController {

    private HealthDataService healthDataService;
    
    public void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }
    
    public Result appendHealthData(Long studyId, Long trackerId) throws Exception {
        UserSession session = getSession();
        
        JsonNode node = request().body().asJson();
        HealthDataEntry entry = HealthDataEntryImpl.fromJson(node);
        
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        String id = healthDataService.appendHealthData(key, entry);
        return jsonResult(new JsonPayload<IdHolder>(new IdHolder(id)));
    }

    public Result getAllHealthData(Long studyId, Long trackerId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        
        List<HealthDataEntry> entries = healthDataService.getAllHealthData(key);
        return jsonResult(new JsonPayload<List<HealthDataEntry>>(HealthDataEntry.class.getName(), entries));
    }
    
    public Result getHealthDataByDate(Long studyId, Long trackerId, Long date) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        List<HealthDataEntry> entries = healthDataService.getHealthDataByDateRange(key, new Date(date), new Date(date));
        return jsonResult(new JsonPayload<List<HealthDataEntry>>(HealthDataEntry.class.getName(), entries));
    }
    
    public Result getHealthDataByDateRange(Long studyId, Long trackerId, Long startDate, Long endDate) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        List<HealthDataEntry> entries = healthDataService.getHealthDataByDateRange(key, new Date(startDate), new Date(endDate));
        return jsonResult(new JsonPayload<List<HealthDataEntry>>(HealthDataEntry.class.getName(), entries));
    }
    
    public Result getHealthDataEntry(Long studyId, Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        HealthDataEntry entry = healthDataService.getHealthDataEntry(key, recordId);
        return jsonResult(new JsonPayload<HealthDataEntry>(HealthDataEntry.class.getName(), entry));
    }
    
    public Result updateHealthDataEntry(Long studyId, Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        JsonNode node = request().body().asJson();
        HealthDataEntry entry = HealthDataEntryImpl.fromJson(node);
        
        healthDataService.updateHealthDataEntry(key, entry);
        return jsonResult(new JsonPayload<HealthDataEntry>(HealthDataEntry.class.getName(), entry));
    }
    
    public Result deleteHealthDataEntry(Long studyId, Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        HealthDataKey key = new HealthDataKey(studyId, trackerId, session.getSessionToken());
        healthDataService.deleteHealthDataEntry(key, recordId);
        return jsonResult("Entry deleted.");
    }
    
}
