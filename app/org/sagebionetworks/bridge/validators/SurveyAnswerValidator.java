package org.sagebionetworks.bridge.validators;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
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
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.google.common.collect.Sets;

public class SurveyAnswerValidator implements Validator {

    private static final long FIVE_MINUTES = 5 * 60 * 1000;
    
    private static final DurationConstraints DURATION_CONSTRAINTS = new DurationConstraints();
    private static final StringConstraints STRING_CONSTRAINTS = new StringConstraints();
    private static final IntegerConstraints INTEGER_CONSTRAINTS = new IntegerConstraints();
    private static final DecimalConstraints DECIMAL_CONSTRAINTS = new DecimalConstraints();
    private static final BooleanConstraints BOOLEAN_CONSTRAINTS = new BooleanConstraints();
    private static final DateConstraints DATE_CONSTRAINTS = new DateConstraints();
    private static final TimeConstraints TIME_CONSTRAINTS = new TimeConstraints();

    private static Set<String> BOOLEAN_VALUES = Sets.newHashSet("true", "false");

    private SurveyQuestion question;
    
    public SurveyAnswerValidator(SurveyQuestion question) {
        this.question = question;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return SurveyAnswer.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        SurveyAnswer answer = (SurveyAnswer)object;
        
        if (question == null) {
            errors.reject("Answer does not match a question with the GUID of: " + answer.getQuestionGuid());
            return;
        }
        errors.pushNestedPath(question.getIdentifier());
        
        if (answer.getAnsweredOn() == 0L) {
            rejectField(errors, "answeredOn", "it requires the date the user answered the question",
                    answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getClient())) {
            rejectField(errors, "client", "it requires a client string", answer.getQuestionGuid());
        }
        if (StringUtils.isBlank(answer.getQuestionGuid())) {
            rejectField(errors, "questionGuid", "it requires a question GUID", answer.getQuestionGuid());
        }
        if (answer.isDeclined()) {
            answer.setAnswer(null);
            answer.setAnswers(null);
        } else if (hasNoAnswer(answer)) {
            rejectField(errors, "answer", "it was not declined but has no answer");
        } else if (expectsMultipleValues()) {
            validateType(errors, (MultiValueConstraints) question.getConstraints(), answer.getAnswers());
        } else if (isMultiValue()) {
            validateType(errors, (MultiValueConstraints) question.getConstraints(), answer.getAnswer());
        } else {
            switch (question.getConstraints().getDataType()) {
            case DURATION:
                validateType(errors, (DurationConstraints) question.getConstraints(), answer.getAnswer());
                break;
            case STRING:
                validateType(errors, (StringConstraints) question.getConstraints(), answer.getAnswer());
                break;
            case INTEGER:
                validateType(errors, (IntegerConstraints) question.getConstraints(), answer.getAnswer());
                break;
            case DECIMAL:
                validateType(errors, (DecimalConstraints) question.getConstraints(), answer.getAnswer());
                break;
            case BOOLEAN:
                validateType(errors, (BooleanConstraints) question.getConstraints(), answer.getAnswer());
                break;
            case DATE:
            case DATETIME:
                validateType(errors, (TimeBasedConstraints) question.getConstraints(), answer.getAnswer());
                break;
            case TIME:
                validateType(errors, (TimeConstraints) question.getConstraints(), answer.getAnswer());
                break;
            }
        }
        errors.popNestedPath();
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

    private void validateType(Errors errors, TimeConstraints constraints, String answer) {
        try {
            LocalTime.parse(answer);
        } catch(Throwable t) {
            rejectField(errors, "constraints", "%s is not a valid 8601 time value (24 hr 'HH:mm:ss' format, no time zone, seconds optional)", answer);
        }
    }

    private void validateType(Errors errors, TimeBasedConstraints con, String answer) {
        long time = 0;
        try {
            time = DateUtils.convertToMillisFromEpoch(answer);
        } catch(Throwable t) {
            rejectField(errors, "constraints", "%s is not a valid 8601 date/datetime string", answer);
            return;
        }
        // add 5 minutes of leniency to this test because different machines may 
        // report different times, we're really trying to catch user input at a 
        // coarser level of time reporting than milliseconds.
        long now = (DateUtils.getCurrentMillisFromEpoch()+FIVE_MINUTES);
        if (!con.getAllowFuture() && time > now) {
            rejectField(errors, "constraints", "%s is not allowed to have a future value after %s", time, now);
        }
    }

    private void validateType(Errors errors, DecimalConstraints con, String answer) {
        try {
            double value = Double.parseDouble(answer);
            if (con.getMinValue() != null && value < con.getMinValue()) {
                rejectField(errors, "constraints", "%s is lower than the minimum value of %s", answer, con.getMinValue());
            }
            if (con.getMaxValue() != null && value > con.getMaxValue()) {
                rejectField(errors, "constraints", "%s is higher than the maximum value of %s", answer, con.getMaxValue());
            }
            // TODO: Now that we are keeping strings, it should be possible to test steps, and 
            // introduce a precision constraint as well.
            /* if (step != null && value % step != 0) {
                messages.add("%s is not a step value of %s", Double.toString(value), step);
            }*/
        } catch(NumberFormatException e) {
            rejectField(errors, "constraints", "%s is not a valid decimal number", answer);
        }
    }

    private void validateType(Errors errors, IntegerConstraints con, String answer) {
        try {
            int value = Integer.parseInt(answer);
            if (con.getMinValue() != null && value < con.getMinValue()) {
                rejectField(errors, "constraints", "%s is lower than the minimum value of %s", answer, con.getMinValue());
            }
            if (con.getMaxValue() != null && value > con.getMaxValue()) {
                rejectField(errors, "constraints", "%s is higher than the maximum value of %s", answer, con.getMaxValue());
            }
            if (con.getStep() != null && value % con.getStep() != 0) {
                rejectField(errors, "constraints", "%s is not a step value of %s", answer, con.getStep());
            }
        } catch(NumberFormatException e) {
            rejectField(errors, "constraints", "%s is not a valid integer", answer);
        }
    }

    private void validateType(Errors errors, StringConstraints con, String answer) {
        if (con.getMinLength() != null && answer.length() < con.getMinLength()) {
            rejectField(errors, "constraints", "%s is shorter than %s characters", answer, con.getMinLength());
        } else if (con.getMaxLength() != null && answer.length() > con.getMaxLength()) {
            rejectField(errors, "constraints", "%s is longer than %s characters", answer, con.getMaxLength());
        }
        if (StringUtils.isNotBlank(con.getPattern()) && !answer.matches(con.getPattern())) {
            rejectField(errors, "constraints", "%s does not match the regular expression /%s/", answer, con.getPattern());
        }
    }

    private void validateType(Errors errors, DurationConstraints constraints, String answer) {
        try {
            // TODO: The docs don't say it throws an exception when wrong...
            Period.parse(answer);
        } catch(Throwable t) {
            rejectField(errors, "constraints", "%s is not a valid ISO 8601 duration string", answer);
        }
    }
    private void validateType(Errors errors, BooleanConstraints con, String answer) {
        if (!BOOLEAN_VALUES.contains(answer)) {
            rejectField(errors, "constraints", "%s is not a boolean", answer);
        }
    }
    private void validateType(Errors errors, MultiValueConstraints con, List<String> answers) {
        // Then we're concerned with the array of values under "answers"
        for (int i=0; i < answers.size(); i++) {
            validateMultiValueType(errors, con, answers.get(i));
        }
        if (!con.getAllowOther()) {
            for (int i=0; i < answers.size(); i++) {
                if (!isEnumeratedValue(con, answers.get(i))) {
                    rejectField(errors, "constraints", "%s is not an enumerated value for this question", answers.get(i));
                }
            }
        }
    }
    private void validateType(Errors errors, MultiValueConstraints con, String answer) {
        // Then we're concerned with the one answer
        validateMultiValueType(errors, con, answer);
        if (!con.getAllowOther() && !isEnumeratedValue(con, answer)) {
            rejectField(errors, "constraints", "%s is not an enumerated value for this question", answer);
        }
    }
    
    private void validateMultiValueType(Errors errors, MultiValueConstraints con, String answer) {
        switch(con.getDataType()) {
        case DURATION:
            validateType(errors, DURATION_CONSTRAINTS, answer);
            break;
        case STRING:
            validateType(errors, STRING_CONSTRAINTS, answer);
            break;
        case INTEGER:
            validateType(errors, INTEGER_CONSTRAINTS, answer);
            break;
        case DECIMAL:
            validateType(errors, DECIMAL_CONSTRAINTS, answer);
            break;
        case BOOLEAN:
            validateType(errors, BOOLEAN_CONSTRAINTS, answer);
            break;
        case DATE:
        case DATETIME:
            validateType(errors, DATE_CONSTRAINTS, answer);
            break;
        case TIME:
            validateType(errors, TIME_CONSTRAINTS, answer);
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
    private void rejectField(Errors errors, String field, String message, Object... args) {
        errors.rejectValue(field, message, args, message);
    }
}
