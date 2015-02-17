package controllers;

import global.JsonSchemaValidator;

import java.util.List;

import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Tracker;
import org.sagebionetworks.bridge.models.studies.TrackerInfo;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class TrackerController extends BaseController {

    private JsonSchemaValidator jsonSchemaValidator;
    
    public void setJsonSchemaValidator(JsonSchemaValidator jsonSchemaValidator) {
        this.jsonSchemaValidator = jsonSchemaValidator;
    }
    
    public Result getTrackers() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        List<TrackerInfo> infos = Lists.newArrayList();
        for (String trackerId : study.getTrackers()) {
            Tracker tracker = studyService.getTracker(trackerId);
            infos.add(new TrackerInfo(tracker));
        }
        return okResult(infos);
    }
    
    public Result getTrackerSchema(String trackerId) throws Exception {
        getAuthenticatedSession();
        Tracker tracker = studyService.getTracker(trackerId);
        JsonNode node = jsonSchemaValidator.getSchemaAsNode(tracker);
        return okResult(node);
    }

}
