package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.Activity;

public class ActivityValidatorTest {

    @Test
    public void rejectsWithoutLabel() {
        try {
            new Activity.Builder().withPublishedSurvey("identifier", "BBB").build();
        } catch(InvalidEntityException e) {
            assertEquals("label cannot be missing, null, or blank", e.getErrors().get("label").get(0));
        }
    }
    
    @Test
    public void rejectsSurveyWithoutIdentifier() {
        try {
            new Activity.Builder().withLabel("Label").withSurvey(null, "BBB", null).build();
        } catch(InvalidEntityException e) {
            assertEquals("survey.identifier cannot be missing, null, or blank", e.getErrors().get("survey.identifier").get(0));
        }
    }
    
    @Test
    public void rejectsSurveyWithoutGuid() {
        try {
            new Activity.Builder().withLabel("Label").withSurvey("identifier", null, null).build();
        } catch(InvalidEntityException e) {
            assertEquals("survey.guid cannot be missing, null, or blank", e.getErrors().get("survey.guid").get(0));
        }
    }
    
    @Test
    public void rejectsTaskWithoutIdentifier() {
        try {
            new Activity.Builder().withLabel("Label").withTask(null).build();
        } catch(InvalidEntityException e) {
            assertEquals("task.identifier cannot be missing, null, or blank", e.getErrors().get("task.identifier").get(0));
        }
    }
    
    @Test
    public void rejectsSurveyResponseWithoutSurvey() {
        try {
            new Activity.Builder().withLabel("Label").withSurveyResponse(null).build();
        } catch(InvalidEntityException e) {
            assertEquals("Activity has a survey reference, so it must also reference the survey", e.getErrors().get("Activity").get(0));
        }
    }
    
    @Test
    public void rejectsSurveyResponseWithoutGuid() {
        try {
            new Activity.Builder().withLabel("Label").withSurveyResponse(null).withPublishedSurvey("identifier", "guid").build();
        } catch(InvalidEntityException e) {
            assertEquals("surveyResponse.guid cannot be missing, null, or blank", e.getErrors().get("surveyResponse.guid").get(0));
        }
    }
    /*
    @Test
    public void rejectsActivityWithoutLabel() {
        Activity activity = new Activity(null, "Label Detail", null, null, null);

        validator.validate(activity, errors);
        assertEquals("cannot be missing, null, or blank", errors.getFieldError("label").getCode());
    }
    
    @Test
    public void rejectsSurveysWithInvalidSurveyReference() {
        SurveyReference reference = new SurveyReference(null, null, (String)null);
        Activity activity = new Activity("Label", null, null, reference, null);

        validator.validate(activity, errors);
        assertEquals("does not match the URL for this activity", errors.getFieldError("survey.guid").getCode());
        // Can no longer happen because the reference is always created from the ref string.
        // assertEquals("does not match the URL for this activity", errors.getFieldError("survey.createdOn").getCode());
        
        errors =  new MapBindingResult(Maps.newHashMap(), "Activity");
        when(activity.getSurvey()).thenReturn(null);
        validator.validate(activity, errors);
        assertEquals("cannot be null", errors.getFieldError("survey").getCode());
    }
    */
}
