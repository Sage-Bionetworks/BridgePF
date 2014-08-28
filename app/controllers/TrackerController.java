package controllers;

import global.JsonSchemaValidator;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.TrackerInfo;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class TrackerController extends BaseController {

    private JsonSchemaValidator jsonSchemaValidator;
    private StudyControllerService studyControllerService;
    
    public void setJsonSchemaValidator(JsonSchemaValidator jsonSchemaValidator) {
        this.jsonSchemaValidator = jsonSchemaValidator;
    }

    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }
    
    public Result getTrackers() throws Exception {
        Study study = studyControllerService.getStudyByHostname(request());
        List<TrackerInfo> infos = Lists.newArrayList();
        for (Tracker tracker : study.getTrackers()) {
            infos.add(new TrackerInfo(tracker));
        }
        return ok(constructJSON(infos));
    }
    
    
    public Result getTrackerSchema(Long trackerId) throws Exception {
        Study study = studyControllerService.getStudyByHostname(request());
        Tracker tracker = study.getTrackerById(trackerId);
        JsonNode node = jsonSchemaValidator.getSchemaAsNode(tracker);
        return ok(constructJSON(node));
    }

}
