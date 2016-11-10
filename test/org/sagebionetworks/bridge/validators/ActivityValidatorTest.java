package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;

import com.google.common.collect.Sets;

public class ActivityValidatorTest {

    private static final Set<String> EMPTY_TASKS = Collections.emptySet(); 
    
    @Test
    public void rejectsWithoutLabel() {
        try {
            Activity activity = new Activity.Builder().withPublishedSurvey("identifier", "BBB").build();
            Validate.entityThrowingException(new ActivityValidator(EMPTY_TASKS), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("label cannot be missing, null, or blank", e.getErrors().get("label").get(0));
        }
    }

    @Test
    public void noSources() {
        try {
            Activity activity = new Activity.Builder().withLabel("Label").build();
            Validate.entityThrowingException(new ActivityValidator(EMPTY_TASKS), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("activity must have exactly one of compound activity, task, or survey", e.getErrors()
                    .get("activity").get(0));
        }
    }

    @Test
    public void multipleSources() {
        try {
            CompoundActivity compoundActivity = new CompoundActivity.Builder().withTaskIdentifier("combo-activity")
                    .build();
            Activity activity = new Activity.Builder().withLabel("Label").withCompoundActivity(compoundActivity)
                    .withPublishedSurvey("My Survey", "CCC").build();
            Validate.entityThrowingException(new ActivityValidator(ImmutableSet.of("combo-activity")), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("activity must have exactly one of compound activity, task, or survey", e.getErrors()
                    .get("activity").get(0));
        }
    }

    @Test
    public void validCompoundActivity() {
        CompoundActivity compoundActivity = new CompoundActivity.Builder().withTaskIdentifier("combo-activity")
                .build();
        Activity activity = new Activity.Builder().withLabel("Label").withCompoundActivity(compoundActivity).build();
        Validate.entityThrowingException(new ActivityValidator(ImmutableSet.of("combo-activity")), activity);
    }

    @Test
    public void compoundActivityWithoutTaskIdentifier() {
        try {
            CompoundActivity compoundActivity = new CompoundActivity.Builder().build();
            Activity activity = new Activity.Builder().withLabel("Label").withCompoundActivity(compoundActivity)
                    .build();
            Validate.entityThrowingException(new ActivityValidator(ImmutableSet.of("combo-activity")), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("compoundActivity.taskIdentifier cannot be missing, null, or blank", e.getErrors()
                    .get("compoundActivity.taskIdentifier").get(0));
        }
    }

    @Test
    public void compoundActivityWithInvaludTaskIdentifier() {
        try {
            CompoundActivity compoundActivity = new CompoundActivity.Builder().withTaskIdentifier("bad-activity")
                    .build();
            Activity activity = new Activity.Builder().withLabel("Label").withCompoundActivity(compoundActivity)
                    .build();
            Validate.entityThrowingException(new ActivityValidator(ImmutableSet.of("combo-activity")), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("compoundActivity.taskIdentifier 'bad-activity' is not in enumeration: combo-activity.",
                    e.getErrors().get("compoundActivity.taskIdentifier").get(0));
        }
    }

    @Test
    public void surveyWithoutIdentifierIsOk() {
        Activity activity = new Activity.Builder().withLabel("Label").withSurvey(null, "BBB", null).build();
        Validate.entityThrowingException(new ActivityValidator(EMPTY_TASKS), activity);
    }
    
    @Test
    public void rejectsSurveyWithoutGuid() {
        try {
            Activity activity = new Activity.Builder().withLabel("Label").withSurvey("identifier", null, null).build();
            Validate.entityThrowingException(new ActivityValidator(EMPTY_TASKS), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("survey.guid cannot be missing, null, or blank", e.getErrors().get("survey.guid").get(0));
        }
    }
    
    @Test
    public void rejectsTaskWithoutIdentifier() {
        try {
            Activity activity = new Activity.Builder().withLabel("Label").withTask((String)null).build();
            Validate.entityThrowingException(new ActivityValidator(EMPTY_TASKS), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("task.identifier cannot be missing, null, or blank", e.getErrors().get("task.identifier").get(0));
        }
    }
    
    @Test
    public void rejectsTaskIdentifierNotDeclaredForStudy() {
        try {
            Activity activity = new Activity.Builder().withLabel("Label").withTask("foo").build();
            Validate.entityThrowingException(new ActivityValidator(null), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("task.identifier 'foo' is not in enumeration: <no task identifiers declared>", e.getErrors().get("task.identifier").get(0));
        }
    }
    
    @Test
    public void rejectsTaskIdentifierNotInList() {
        try {
            Activity activity = new Activity.Builder().withLabel("Label").withTask("foo").build();
            Validate.entityThrowingException(new ActivityValidator(Sets.newHashSet("bar", "baz")), activity);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("task.identifier 'foo' is not in enumeration: bar, baz.", e.getErrors().get("task.identifier").get(0));
        }
    }
    
    @Test
    public void declaredTaskIdentifierOK() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask("foo").build();
        Validate.entityThrowingException(new ActivityValidator(Sets.newHashSet("foo")), activity);
    }
}
