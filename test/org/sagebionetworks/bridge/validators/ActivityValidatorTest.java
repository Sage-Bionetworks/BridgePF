package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.Collections;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;

import com.google.common.collect.Sets;

public class ActivityValidatorTest {

    private static final Set<String> EMPTY_TASKS = Collections.emptySet();
    private static final Validator VALIDATOR = new ActivityValidator(EMPTY_TASKS);
    private static final Validator VALIDATOR_WITH_TASKS = new ActivityValidator(ImmutableSet.of("combo-activity"));
    
    @Test
    public void rejectsWithoutLabel() {
        Activity activity = new Activity.Builder().withPublishedSurvey("identifier", "BBB").build();
        assertValidatorMessage(VALIDATOR, activity, "label", "cannot be missing, null, or blank");
    }
    
    @Test
    public void rejectsWithoutGuid() {
        Activity activity = new Activity.Builder().withLabel("label").withPublishedSurvey("identifier", "BBB").build();
        assertValidatorMessage(VALIDATOR, activity, "guid", "cannot be missing, null, or blank");
    }

    @Test
    public void noSources() {
        Activity activity = new Activity.Builder().withLabel("Label").build();
        assertValidatorMessage(VALIDATOR, activity, "activity", "must have exactly one of compound activity, task, or survey");
    }

    @Test
    public void multipleSources() {
        CompoundActivity compoundActivity = new CompoundActivity.Builder().withTaskIdentifier("combo-activity").build();
        Activity activity = new Activity.Builder().withLabel("Label").withCompoundActivity(compoundActivity)
                .withPublishedSurvey("My Survey", "CCC").build();
        assertValidatorMessage(VALIDATOR_WITH_TASKS, activity, "activity",
                "must have exactly one of compound activity, task, or survey");
    }

    @Test
    public void validCompoundActivity() {
        CompoundActivity compoundActivity = new CompoundActivity.Builder().withTaskIdentifier("combo-activity")
                .build();
        Activity activity = new Activity.Builder().withGuid("guid").withLabel("Label").withCompoundActivity(compoundActivity).build();
        Validate.entityThrowingException(new ActivityValidator(ImmutableSet.of("combo-activity")), activity);
    }

    @Test
    public void compoundActivityWithoutTaskIdentifier() {
        CompoundActivity compoundActivity = new CompoundActivity.Builder().build();
        Activity activity = new Activity.Builder().withLabel("Label").withCompoundActivity(compoundActivity).build();
        assertValidatorMessage(VALIDATOR_WITH_TASKS, activity, "compoundActivity.taskIdentifier", "cannot be missing, null, or blank");
    }

    @Test
    public void surveyWithoutIdentifierIsOk() {
        Activity activity = new Activity.Builder().withGuid("guid").withLabel("Label").withSurvey(null, "BBB", null).build();
        Validate.entityThrowingException(new ActivityValidator(EMPTY_TASKS), activity);
    }
    
    @Test
    public void rejectsSurveyWithoutGuid() {
        Activity activity = new Activity.Builder().withGuid("guid").withLabel("Label").withSurvey("identifier", null, null).build();
        assertValidatorMessage(VALIDATOR, activity, "survey.guid", "cannot be missing, null, or blank");
    }
    
    @Test
    public void rejectsTaskWithoutIdentifier() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask((String)null).build();
        assertValidatorMessage(VALIDATOR, activity, "task.identifier", "cannot be missing, null, or blank");
    }
    
    @Test
    public void rejectsTaskIdentifierNotDeclaredForStudy() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask("foo").build();
        assertValidatorMessage(VALIDATOR, activity, "task.identifier", "'foo' is not in enumeration: <no task identifiers declared>");
    }
    
    @Test
    public void rejectsTaskIdentifierNotInList() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask("foo").build();
        assertValidatorMessage(new ActivityValidator(Sets.newHashSet("bar","baz")), activity, "task.identifier", "'foo' is not in enumeration: bar, baz");
    }
    
    @Test
    public void declaredTaskIdentifierOK() {
        Activity activity = new Activity.Builder().withGuid("guid").withLabel("Label").withTask("foo").build();
        Validate.entityThrowingException(new ActivityValidator(Sets.newHashSet("foo")), activity);
    }
}
