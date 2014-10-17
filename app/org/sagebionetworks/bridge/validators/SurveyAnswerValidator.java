package org.sagebionetworks.bridge.validators;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeBasedConstraints;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;

import com.google.common.collect.Sets;

public class SurveyAnswerValidator implements Validator<SurveyAnswer> {

    private static final long FIVE_MINUTES = 5 * 60 * 1000;

    private Set<String> BOOLEAN_VALUES = Sets.newHashSet("true", "false");

    private SurveyQuestion question;
    
    public SurveyAnswerValidator(SurveyQuestion question) {
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
            answer.setAnswers(null);
        } 
        else if (hasNoAnswer(answer)) {
            messages.add("it was not declined but has no answer");
        } 
        else if (expectsMultipleValues()) {
            validateType(messages, (MultiValueConstraints) question.getConstraints(), answer.getAnswers());
        } 
        else if (isMultiValue()) {
            validateType(messages, (MultiValueConstraints) question.getConstraints(), answer.getAnswer());
        } 
        else {
            switch(question.getConstraints().getDataType()) {
            case DURATION:
                validateType(messages, (DurationConstraints)question.getConstraints(), answer.getAnswer());
                break;
            case STRING:
                validateType(messages, (StringConstraints)question.getConstraints(), answer.getAnswer());
                break;
            case INTEGER:
                validateType(messages, (IntegerConstraints)question.getConstraints(), answer.getAnswer());
                break;
            case DECIMAL:
                validateType(messages, (DecimalConstraints)question.getConstraints(), answer.getAnswer());
                break;
            case BOOLEAN:
                validateType(messages, (BooleanConstraints)question.getConstraints(), answer.getAnswer());
                break;
            case DATE:
            case DATETIME:
                validateType(messages, (TimeBasedConstraints)question.getConstraints(), answer.getAnswer());
                break;
            case TIME:
                validateType(messages, (TimeConstraints)question.getConstraints(), answer.getAnswer());
                break;
            }
        }
        if (!messages.isEmpty()) {
            String message = String.format("Answer for question '%s' is invalid: %s", question.getIdentifier(), messages.join());
            throw new InvalidEntityException(answer, message);
        }
    }
    
    private boolean isMultiValue() {
        return (question.getConstraints() instanceof MultiValueConstraints);
    }
    
    private boolean expectsMultipleValues() {
        return isMultiValue() && ((MultiValueConstraints)question.getConstraints()).getAllowMultiple();
    }
    
    private boolean hasNoAnswer(SurveyAnswer answer) {
        if (expectsMultipleValues()) {
            return BridgeUtils.isEmpty(answer.getAnswers());
        }
        return StringUtils.isEmpty(answer.getAnswer());
    }

    private void validateType(Messages messages, TimeConstraints constraints, String answer) {
        try {
            LocalTime.parse(answer);
        } catch(Throwable t) {
            messages.add("%s is not a valid 8601 time value (24 hr 'HH:mm:ss' format, no time zone, seconds optional)", answer);
        }
    }

    private void validateType(Messages messages, TimeBasedConstraints con, String answer) {
        long time = 0;
        try {
            time = DateUtils.convertToMillisFromEpoch(answer);
        } catch(Throwable t) {
            messages.add("%s is not a valid 8601 date/datetime string", answer);
            return;
        }
        // add 5 minutes of leniency to this test because different machines may 
        // report different times, we're really trying to catch user input at a 
        // coarser level of time reporting than milliseconds.
        long now = (DateUtils.getCurrentMillisFromEpoch()+FIVE_MINUTES);
        if (!con.getAllowFuture() && time > now) {
            messages.add("%s is not allowed to have a future value after %s", time, now);
        }
    }

    private void validateType(Messages messages, DecimalConstraints con, String answer) {
        try {
            double value = Double.parseDouble(answer);
            if (con.getMinValue() != null && value < con.getMinValue()) {
                messages.add("%s is lower than the minimum value of %s", answer, con.getMinValue());
            }
            if (con.getMaxValue() != null && value > con.getMaxValue()) {
                messages.add("%s is higher than the maximum value of %s", answer, con.getMaxValue());
            }
            // TODO: Now that we are keeping strings, it should be possible to test steps, and 
            // introduce a precision constraint as well.
            /* if (step != null && value % step != 0) {
                messages.add("%s is not a step value of %s", Double.toString(value), step);
            }*/
        } catch(NumberFormatException e) {
            messages.add("%s is not a valid decimal number", answer);
        }
    }

    private void validateType(Messages messages, IntegerConstraints con, String answer) {
        try {
            int value = Integer.parseInt(answer);
            if (con.getMinValue() != null && value < con.getMinValue()) {
                messages.add("%s is lower than the minimum value of %s", answer, con.getMinValue());
            }
            if (con.getMaxValue() != null && value > con.getMaxValue()) {
                messages.add("%s is higher than the maximum value of %s", answer, con.getMaxValue());
            }
            if (con.getStep() != null && value % con.getStep() != 0) {
                messages.add("%s is not a step value of %s", answer, con.getStep());
            }
        } catch(NumberFormatException e) {
            messages.add("%s is not a valid integer", answer);
        }
    }

    private void validateType(Messages messages, StringConstraints con, String answer) {
        if (con.getMinLength() != null && answer.length() < con.getMinLength()) {
            messages.add("%s is shorter than %s characters", answer, con.getMinLength());
        } else if (con.getMaxLength() != null && answer.length() > con.getMaxLength()) {
            messages.add("%s is longer than %s characters", answer, con.getMaxLength());
        }
        if (StringUtils.isNotBlank(con.getPattern()) && !answer.matches(con.getPattern())) {
            messages.add("%s does not match the regular expression /%s/", answer, con.getPattern());
        }
    }

    private void validateType(Messages messages, DurationConstraints constraints, String answer) {
        try {
            // TODO: The docs don't say it throws an exception when wrong...
            Period.parse(answer);
        } catch(Throwable t) {
            messages.add("%s is not a valid ISO 8601 duration string", answer);
        }
    }
    private void validateType(Messages messages, BooleanConstraints con, String answer) {
        if (!BOOLEAN_VALUES.contains(answer)) {
            messages.add("%s is not a boolean", answer);
        }
    }
    private void validateType(Messages messages, MultiValueConstraints con, List<String> answers) {
        // Then we're concerned with the array of values under "answers"
        for (int i=0; i < answers.size(); i++) {
            validateMultiValueType(messages, con, answers.get(i));
        }
        if (!con.getAllowOther()) {
            for (int i=0; i < answers.size(); i++) {
                if (!isEnumeratedValue(con, answers.get(i))) {
                    messages.add("%s is not an enumerated value for this question", answers.get(i));
                }
            }
        }
    }
    private void validateType(Messages messages, MultiValueConstraints con, String answer) {
        // Then we're concerned with the one answer
        validateMultiValueType(messages, con, answer);
        if (!con.getAllowOther() && !isEnumeratedValue(con, answer)) {
            messages.add("%s is not an enumerated value for this question", answer);
        }
    }
    private void validateMultiValueType(Messages messages, MultiValueConstraints con, String answer) {
        switch(con.getDataType()) {
        case DURATION:
            validateType(messages, new DurationConstraints(), answer);
            break;
        case STRING:
            validateType(messages, new StringConstraints(), answer);
            break;
        case INTEGER:
            validateType(messages, new IntegerConstraints(), answer);
            break;
        case DECIMAL:
            validateType(messages, new DecimalConstraints(), answer);
            break;
        case BOOLEAN:
            validateType(messages, new BooleanConstraints(), answer);
            break;
        case DATE:
        case DATETIME:
            validateType(messages, new DateConstraints(), answer);
            break;
        case TIME:
            validateType(messages, new TimeConstraints(), answer);
            break;
        }
    }
    private boolean isEnumeratedValue(MultiValueConstraints con, String value) {
        for (SurveyQuestionOption option : con.getEnumeration()) {
            if (option.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }
    
}
