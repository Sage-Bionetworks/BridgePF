package controllers;

import global.JsonSchemaValidator;

import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.TrackerInfo;

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
    
    
    public Result getTrackerSchema(String trackerIdString) throws Exception {
        Long trackerId = BridgeUtils.parseLong(trackerIdString);
        getAuthenticatedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        Tracker tracker = study.getTrackerById(trackerId);
        JsonNode node = jsonSchemaValidator.getSchemaAsNode(tracker);
        return okResult(node);
    }

}
