package controllers;

import global.JsonSchemaValidator;

import java.util.List;

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
        getAuthenticatedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        List<TrackerInfo> infos = Lists.newArrayList();
        for (Tracker tracker : study.getTrackers()) {
            infos.add(new TrackerInfo(tracker));
        }
        return okResult(infos);
    }
    
    
    public Result getTrackerSchema(String identifier) throws Exception {
        getAuthenticatedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerByIdentifier(identifier);
        JsonNode node = jsonSchemaValidator.getSchemaAsNode(tracker);
        return okResult(node);
    }

}
