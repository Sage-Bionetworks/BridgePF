package org.sagebionetworks.bridge.validators;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
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

public class SurveyAnswerValidator implements Validator<SurveyAnswer> {

    private static final long FIVE_MINUTES = 5 * 60 * 1000;

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
            messages.add("it requires an answer (it wasn't declined)");
        } else if (question.getConstraints().getClass() == MultiValueConstraints.class) {
            validateType(messages, (MultiValueConstraints)question.getConstraints(), answer);
        } else {
            switch(question.getConstraints().getDataType()) {
            case DURATION:
                validateType(messages, (DurationConstraints)question.getConstraints(), answer);
                break;
            case STRING:
                validateType(messages, (StringConstraints)question.getConstraints(), answer);
                break;
            case INTEGER:
                validateType(messages, (IntegerConstraints)question.getConstraints(), answer);
                break;
            case DECIMAL:
                validateType(messages, (DecimalConstraints)question.getConstraints(), answer);
                break;
            case BOOLEAN:
                validateType(messages, (BooleanConstraints)question.getConstraints(), answer);
                break;
            case DATE:
            case DATETIME:
                validateType(messages, (TimeBasedConstraints)question.getConstraints(), answer);
                break;
            case TIME:
                validateType(messages, (TimeConstraints)question.getConstraints(), answer);
                break;
            }
        }
        if (!messages.isEmpty()) {
            String message = String.format("Answer for question '%s' is invalid: %s", question.getIdentifier(), messages.join());
            throw new InvalidEntityException(answer, message);
        }
    }
    
    private void validateType(Messages messages, TimeConstraints constraints, SurveyAnswer answer) {
        try {
            LocalTime.parse((String)answer.getAnswer());    
        } catch(Throwable t) {
            messages.add("%s is not a valid 8601 time value (24 hr 'HH:mm:ss' format, no time zone, seconds optional)", answer.getAnswer());
        }
    }

    private void validateType(Messages messages, TimeBasedConstraints con, SurveyAnswer answer) {
        long time = 0;
        try {
            time = DateUtils.convertToMillisFromEpoch((String)answer.getAnswer());
        } catch(Throwable t) {
            messages.add("%s is not a valid 8601 date/datetime string", answer.getAnswer());
        }
        // add 5 minutes of leniency to this test because different machines may 
        // report different times, we're really trying to catch user input at a 
        // coarser level of time reporting than milliseconds.
        long now = (DateUtils.getCurrentMillisFromEpoch()+FIVE_MINUTES);
        if (!con.getAllowFuture() && time > now) {
            messages.add("%s is not allowed to have a future value after %s", time, now);
        }
    }

    private void validateType(Messages messages, DecimalConstraints con, SurveyAnswer answer) {
        double value = (Double)answer.getAnswer();
        if (con.getMinValue() != null && value < con.getMinValue()) {
            messages.add("%s is lower than the minimum value of %s", Double.toString(value), con.getMinValue());
        }
        if (con.getMaxValue() != null && value > con.getMaxValue()) {
            messages.add("%s is higher than the maximum value of %s", Double.toString(value), con.getMaxValue());
        }
        // Can't enforce this due to the crazy rounding that goes on with floating point numbers. 
        // I am wondering how we can preserve these, short of persisting them as strings. This is 
        // apparently a Hard Problem in Computer Science.
        /* if (step != null && value % step != 0) {
            messages.add("%s is not a step value of %s", Double.toString(value), step);
        }*/
    }

    private void validateType(Messages messages, IntegerConstraints con, SurveyAnswer answer) {
        int value = (Integer)answer.getAnswer();
        if (con.getMinValue() != null && value < con.getMinValue()) {
            messages.add("%s is lower than the minimum value of %s", value, con.getMinValue());
        }
        if (con.getMaxValue() != null && value > con.getMaxValue()) {
            messages.add("%s is higher than the maximum value of %s", value, con.getMaxValue());
        }
        if (con.getStep() != null && value % con.getStep() != 0) {
            messages.add("%s is not a step value of %s", value, con.getStep());
        }
    }

    private void validateType(Messages messages, StringConstraints con, SurveyAnswer answer) {
        String value = (String)answer.getAnswer();
        if (con.getMinLength() != null && value.length() < con.getMinLength()) {
            messages.add("%s is shorter than %s characters", value, con.getMinLength());
        } else if (con.getMaxLength() != null && value.length() > con.getMaxLength()) {
            messages.add("%s is longer than %s characters", value, con.getMaxLength());
        }
        if (StringUtils.isNotBlank(con.getPattern()) && !value.matches(con.getPattern())) {
            messages.add("%s does not match the regular expression /%s/", value, con.getPattern());
        }
    }

    private void validateType(Messages messages, DurationConstraints constraints, SurveyAnswer answer) {
        try {
            Period.parse((String)answer.getAnswer());
        } catch(Throwable t) {
            messages.add("%s is not a valid ISO 8601 duration string", answer.getAnswer());
        }
    }

    private void validateType(Messages messages, BooleanConstraints con, SurveyAnswer answer) {
        if (con.getDataType().getCastClass() != answer.getAnswer().getClass()) {
            messages.add("%s is not a %s value", answer.getAnswer().getClass().getSimpleName(), 
                    con.getDataType().getCastClass().getSimpleName());
        }
    }
    private void validateType(Messages messages, MultiValueConstraints con, SurveyAnswer answer) {
        Object value = answer.getAnswer();
        if (con.getAllowMultiple()) {
            // The only acceptable type here is an array, then validate all members of the array.
            if (!(value instanceof List)) {
                messages.add("Answer should be an array of values");
                return;
            }
            List<?> array = (List<?>)value;
            for (int i=0; i < array.size(); i++) {
                Object obj = array.get(i);
                validateMultiValueType(messages, con, obj, "Array value #"+i);
            }
            if (messages.isEmpty() && !con.getAllowOther()) {
                for (int i=0; i < array.size(); i++) {
                    Object obj = array.get(i);
                    if (!isEnumeratedValue(con, obj)) {
                        messages.add("Answer #%s is not one of the enumerated values for this question", i);
                    }
                }
            }
        } else {
            validateMultiValueType(messages, con, value, "Answer");
            if (messages.isEmpty() && !con.getAllowOther() && !isEnumeratedValue(con, value)) {
                messages.add("Answer is not one of the enumerated values for this question");
            }
        }
    }
    private void validateMultiValueType(Messages messages, MultiValueConstraints con, Object value, String name) {
        if (con.getDataType().getCastClass() != value.getClass()) {
            messages.add("%s is the wrong type (it's %s but it should be %s", name, value.getClass().getSimpleName(),
                    con.getDataType().getCastClass().getSimpleName());
        }
    }
    private boolean isEnumeratedValue(MultiValueConstraints con, Object value) {
        for (SurveyQuestionOption option : con.getEnumeration()) {
            if (option.getValue() instanceof Number && value instanceof Number) {
                double d1 = ((Number)option.getValue()).doubleValue();
                double d2 = ((Number)value).doubleValue();
                if (d1 == d2) {
                    return true;
                }
            } else {
                if (option.getValue().equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }
    
}
