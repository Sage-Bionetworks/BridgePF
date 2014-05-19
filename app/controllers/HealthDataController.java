package controllers;

import global.JsonSchemaValidator;

import java.io.IOException;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import play.mvc.Http.Request;
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
    
    // This is needed or tests fail. It appears to be a bug in Play Framework, that the asJson()
    // method doesn't return a node in that context, possibly because the root object in the JSON 
    // is an array (which is legal). OTOH, if asJson() works, you will get an error if you call 
    // asText(), as Play seems to only allow processing the body content one time in a request.
    private JsonNode requestToJSON(Request request) throws JsonProcessingException, IOException {
        JsonNode node = request().body().asJson();
        if (node == null) {
            ObjectMapper mapper = new ObjectMapper();
            node = mapper.readTree(request().body().asText());
        }
        return node;
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
        
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        List<String> ids = healthDataService.appendHealthData(key, records);
        return jsonResult(new JsonPayload<IdHolder>(new IdHolder(ids)));
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
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, new Date(date), new Date(date));
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(tracker.getType(), entries));
    }
    
    public Result getHealthDataByDateRange(Long trackerId, Long startDate, Long endDate) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        List<HealthDataRecord> entries = healthDataService.getHealthDataByDateRange(key, new Date(startDate), new Date(endDate));
        return jsonResult(new JsonPayload<List<HealthDataRecord>>(tracker.getType(), entries));
    }
    
    public Result getHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        HealthDataRecord record = healthDataService.getHealthDataRecord(key, recordId);
        return jsonResult(new JsonPayload<HealthDataRecord>(tracker.getType(), record));
    }
    
    public Result updateHealthDataRecord(Long trackerId, String recordId) throws Exception {
        UserSession session = getSession();
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        HealthDataKey key = new HealthDataKey(study.getId(), trackerId, session.getSessionToken());
        
        JsonNode node = requestToJSON(request());

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
