package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

public class NotificationTopicValidatorTest {

    private static final Validator VALIDATOR = NotificationTopicValidator.INSTANCE;
    
    private NotificationTopic topic;
    
    @Before
    public void before() {
        topic = NotificationTopic.create();
        topic.setGuid("ABC-DEF");
        topic.setName("Test Topic");
        topic.setShortName("Test");
        topic.setStudyId("test-study");
        topic.setTopicARN("topic:arn");
    }
    
    @Test
    public void isValid() {
        Validate.entityThrowingException(VALIDATOR, topic);
    }
    
    @Test
    public void studyIdRequired() {
        topic.setStudyId(null);
        assertValidatorMessage(VALIDATOR, topic, "studyId", " is required");
    }
    
    @Test
    public void nameRequired() {
        topic.setName(null);
        assertValidatorMessage(VALIDATOR, topic, "name", " is required");
    }

    @Test
    public void shortNameRequired() {
        topic.setShortName(null);
        assertValidatorMessage(VALIDATOR, topic, "shortName", "is required");
    }

    @Test
    public void shortNameTooLong() {
        topic.setShortName("This name is very very long");
        assertValidatorMessage(VALIDATOR, topic, "shortName", "must be 10 characters or less");
    }
}
