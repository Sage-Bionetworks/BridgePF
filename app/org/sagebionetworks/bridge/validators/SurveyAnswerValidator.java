package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;

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
        if (answer.getAnswer() == null && !answer.isDeclined()) {
            messages.add("it requires an answer (it wasn't declined)", answer.getQuestionGuid());
        }
        if (answer.getAnsweredOn() == 0L) {
             messages.add("it requires the date the user answered the question", answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getClient())) {
            messages.add("it requires a client string", answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getQuestionGuid())) {
            messages.add("it requires a question GUID", answer.getQuestionGuid());
        }
        
        Constraints constraints = question.getConstraints();
        if (constraints instanceof BooleanConstraints) {
            validateBoolean(messages, (BooleanConstraints)constraints, answer);
        } else if (constraints instanceof DateConstraints) {
            validateDate(messages, (DateConstraints)constraints, answer);
        } else if (constraints instanceof DateTimeConstraints) {
            validateDateTime(messages, (DateTimeConstraints)constraints, answer);
        } else if (constraints instanceof DecimalConstraints) {
            validateDecimal(messages, (DecimalConstraints)constraints, answer);
        } else if (constraints instanceof DurationConstraints) {
            validateDuration(messages, (DurationConstraints)constraints, answer);
        } else if (constraints instanceof IntegerConstraints) {
            validateInteger(messages, (IntegerConstraints)constraints, answer);
        } else if (constraints instanceof MultiValueConstraints) {
            validateMultiValue(messages, (MultiValueConstraints)constraints, answer);
        } else if (constraints instanceof StringConstraints) {
            validateString(messages, (StringConstraints)constraints, answer);
        } else if (constraints instanceof TimeConstraints) {
            validateTime(messages, (TimeConstraints)constraints, answer);
        }
        if (!messages.isEmpty()) {
            throw new InvalidEntityException(answer, "Answer for question '"+question.getGuid()+"' is invalid: " + messages.join());
        }
    }

    private void validateTime(Messages messages, TimeConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof Number)) {
            messages.add("it does not appear to be a time value");
        }
    }

    private void validateString(Messages messages, StringConstraints constraints, SurveyAnswer answer) {
        // maxLength, minLength, pattern
        if (!(answer.getAnswer() instanceof String)) {
            messages.add("it is not a string");
        }
    }

    private void validateMultiValue(Messages messages, MultiValueConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof String)) {
            messages.add("it is not a string");
        }
    }

    private void validateInteger(Messages messages, IntegerConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof Number)) {
            messages.add("it is not an integer value");
        }
    }

    private void validateDuration(Messages messages, DurationConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof Number)) {
            messages.add("it does not appear to be a duration value");
        }
    }

    private void validateDecimal(Messages messages, DecimalConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof Number)) {
            messages.add("it is not a decimal value");
        }
    }

    private void validateDateTime(Messages messages, DateTimeConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof Number)) {
            messages.add("it does not appear to be a date & time value");
        }
    }

    private void validateDate(Messages messages, DateConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof Number)) {
            messages.add("it does not appear to be a date value");
        }
    }

    private void validateBoolean(Messages messages, BooleanConstraints constraints, SurveyAnswer answer) {
        if (!(answer.getAnswer() instanceof Boolean)) {
            messages.add("it is not a boolean");
        }
    }

    
}
