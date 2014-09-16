package org.sagebionetworks.bridge.validators;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

public class SurveyResponseValidator implements Validator<SurveyResponse> {

    @Override
    public void validateNew(SurveyResponse response) throws InvalidEntityException, EntityAlreadyExistsException {
        Messages messages = new Messages();
        if (StringUtils.isNotBlank(response.getGuid())) {
            messages.add("should not have a GUID");
        }
        if (response.getVersion() != null) {
            messages.add("should not have a versioning ID");
        }
        if (!messages.isEmpty()) {
            throw new EntityAlreadyExistsException(response, "Survey response does not appear to be new: " + messages.join());
        }
    }

    @Override
    public void validate(SurveyResponse response) throws InvalidEntityException {
        Messages messages = new Messages();
        if (StringUtils.isBlank(response.getGuid())) {
            messages.add("GUID is required");
        }
        if (StringUtils.isBlank(response.getHealthCode())) {
            messages.add("health code is required");
        }
        List<SurveyAnswer> answers = response.getAnswers();
        for (SurveyAnswer answer : answers) {
           validateAnswer(messages, answer);
        }
        if (!messages.isEmpty()) {
            throw new EntityAlreadyExistsException(response, "Survey response is not valid: " + messages.join());
        }
    }
    
    void validateAnswer(Messages messages, SurveyAnswer answer) {
        if (answer.getAnswer() == null && !answer.isDeclined()) {
            messages.add("Answer for question '%s' requires an answer (it wasn't declined)", answer.getQuestionGuid());
        }
        if (answer.getAnsweredOn() == 0L) {
             messages.add("Answer for question '%s' requires the date the user answered the question", answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getClient())) {
            messages.add("Answer for question '%s' requires a client string", answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getQuestionGuid())) {
            messages.add("Answer for question '%s' requires a question GUID", answer.getQuestionGuid());
        }
    }

}
