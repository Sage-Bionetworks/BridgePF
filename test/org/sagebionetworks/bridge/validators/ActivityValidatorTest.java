package org.sagebionetworks.bridge.validators;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.springframework.validation.MapBindingResult;

import com.google.common.collect.Maps;

public class ActivityValidatorTest {

    private static final ActivityValidator validator = new ActivityValidator();
    private MapBindingResult errors;
    
    @Before
    public void before() { 
        errors = new MapBindingResult(Maps.newHashMap(), "Activity");
    }
    
    
    @Test
    public void rejectsActivityWithoutType() {
        Activity activity = new Activity("label", null);

        validator.validate(activity, errors);
        assertEquals(2, errors.getErrorCount());
        assertEquals("cannot be null", errors.getFieldError("activityType").getCode());
        assertEquals("cannot be missing, null, or blank", errors.getFieldError("ref").getCode());
    }
    
    @Test
    public void rejectsActivityWithoutLabel() {
        Activity activity = new Activity(null, "task:foo");

        validator.validate(activity, errors);
        assertEquals(1, errors.getErrorCount());
        assertEquals("cannot be missing, null, or blank", errors.getFieldError("label").getCode());
    }
    
    @Test
    public void acceptsSurveysWithAbsoluteHrefs() {
        Activity activity = new Activity("Label", "https://foooserver.com/api/v1/surveys/AAA/revisions/" + DateTime.now().toString());

        validator.validate(activity, errors);
        assertEquals(0, errors.getErrorCount());
        
        activity = new Activity("Label", "http://foooserver.com/api/v1/surveys/AAA/revisions/" + DateTime.now().toString());
        validator.validate(activity, errors);
        assertEquals(0, errors.getErrorCount());
    }
    
    @Test
    public void rejectsSurveysWithInvalidSurveyReference() {
        Activity activity = mock(Activity.class);
        when(activity.getActivityType()).thenReturn(ActivityType.SURVEY);
        when(activity.getLabel()).thenReturn("Label");
        when(activity.getRef()).thenReturn("http://webservices.sagebridge.org/api/v1/surveys/AAA/revisions/" + DateTime.now().toString());
        when(activity.getSurvey()).thenReturn(new SurveyReference("http://webservices.sagebridge.org/api/v1/surveys/CCC/revisions/" + DateTime.now().minusHours(1).toString()));
        
        validator.validate(activity, errors);
        assertEquals("does not match the URL for this activity", errors.getFieldError("survey.guid").getCode());
        // Can no longer happen because the reference is always created from the ref string.
        // assertEquals("does not match the URL for this activity", errors.getFieldError("survey.createdOn").getCode());
        
        errors =  new MapBindingResult(Maps.newHashMap(), "Activity");
        when(activity.getSurvey()).thenReturn(null);
        validator.validate(activity, errors);
        assertEquals("cannot be null", errors.getFieldError("survey").getCode());
    }
}
