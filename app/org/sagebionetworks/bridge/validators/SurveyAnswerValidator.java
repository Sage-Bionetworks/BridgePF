package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

public class SurveyAnswerValidator implements Validator<SurveyAnswer> {

    private SurveyQuestion question;
    
    public SurveyAnswerValidator(SurveyQuestion question, int pos) {
        this.question = question;
    }
    
    @Override
    public void validateNew(SurveyAnswer object) throws InvalidEntityException, EntityAlreadyExistsException {
        throw new UnsupportedOperationException("We don't validate an answer is/isn't new");
    }

    @Override
    public void validate(SurveyAnswer answer) throws InvalidEntityException {
        Messages messages = new Messages();
        if (answer.getAnsweredOn() == 0L) {
             messages.add("it requires the date the user answered the question", answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getClient())) {
            messages.add("it requires a client string", answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getQuestionGuid())) {
            messages.add("it requires a question GUID", answer.getQuestionGuid());
        }
        if (answer.isDeclined()) {
            answer.setAnswer(null);
        } else if (answer.getAnswer() == null) {
            messages.add("it requires an answer (it wasn't declined)", answer.getQuestionGuid());
        } else {
            Constraints con = question.getConstraints();
            boolean isMultipleValue = (con instanceof MultiValueConstraints && ((MultiValueConstraints)con).getAllowMultiple());
            Class<?> answerType = answer.getAnswer().getClass();
            Class<?> expectedType = question.getConstraints().getDataType().getCastClass();
            
            if (isMultipleValue || answerType.isAssignableFrom(expectedType)) {
                question.getConstraints().validate(messages, answer);
            } else {
                messages.add("it expected to be a %s but it is a %s", expectedType.getSimpleName(), answerType.getSimpleName());
            }
        }
        if (!messages.isEmpty()) {
            throw new InvalidEntityException(answer, "Answer for question '" + question.getIdentifier() + "' is invalid: "
                    + messages.join());
        }
    }
}
