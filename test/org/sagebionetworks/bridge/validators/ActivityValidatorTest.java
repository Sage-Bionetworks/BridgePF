package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SurveyResponseReference;

public class ActivityValidatorTest {

    @Test
    public void rejectsWithoutLabel() {
        try {
            new Activity.Builder().withPublishedSurvey("identifier", "BBB").build();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("label cannot be missing, null, or blank", e.getErrors().get("label").get(0));
        }
    }
    
    @Test
    public void surveyWithoutIdentifierIsOk() {
        new Activity.Builder().withLabel("Label").withSurvey(null, "BBB", null).build();
    }
    
    @Test
    public void rejectsSurveyWithoutGuid() {
        try {
            new Activity.Builder().withLabel("Label").withSurvey("identifier", null, null).build();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("survey.guid cannot be missing, null, or blank", e.getErrors().get("survey.guid").get(0));
        }
    }
    
    @Test
    public void rejectsTaskWithoutIdentifier() {
        try {
            new Activity.Builder().withLabel("Label").withTask((String)null).build();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("task.identifier cannot be missing, null, or blank", e.getErrors().get("task.identifier").get(0));
        }
    }
    
    @Test
    public void rejectsSurveyResponseWithoutSurvey() {
        try {
            new Activity.Builder().withLabel("Label").withSurveyResponse((String)null).build();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("Activity has a survey response, so it must also reference the survey", e.getErrors().get("Activity").get(0));
        }
    }
    
    @Test
    public void surveyResponseWithoutIdentifierIsOK() {
        new Activity.Builder().withLabel("Label").withSurveyResponse((SurveyResponseReference)null).withPublishedSurvey("identifier", "guid").build();
    }
}
