package org.sagebionetworks.bridge.validators;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationToIntegerConverter;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.NumericalConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeBasedConstraints;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Unit;
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
            answer.setAnswers(null);
        } else if (hasNoAnswer(answer)) {
            rejectField(errors, "answer", "it was not declined but has no answer");
        } else if (allowsMultipleAnswers()) {
            validateType(errors, (MultiValueConstraints) question.getConstraints(), answer.getAnswers());
        } else {
            String firstAnswer = answer.getAnswers().get(0);
            if (maybeConstrainedToEnumeratedValue()) {
                validateType(errors, (MultiValueConstraints) question.getConstraints(), firstAnswer);    
            } else {
                Constraints con = question.getConstraints();
                switch (con.getDataType()) {
                case DURATION:
                    validateType(errors, (DurationConstraints) con, firstAnswer);
                    break;
                case STRING:
                    validateType(errors, (StringConstraints) con, firstAnswer);
                    break;
                case INTEGER:
                    validateType(errors, (NumericalConstraints) con, firstAnswer, "integer");
                    break;
                case DECIMAL:
                    validateType(errors, (NumericalConstraints) con, firstAnswer, "decimal");
                    break;
                case BOOLEAN:
                    validateType(errors, (BooleanConstraints) con, firstAnswer);
                    break;
                case DATE:
                case DATETIME:
                    validateType(errors, (TimeBasedConstraints) con, firstAnswer);
                    break;
                case TIME:
                    validateType(errors, (TimeConstraints) con, firstAnswer);
                    break;
                }
            }
        }
        errors.popNestedPath();
    }
    
    private boolean maybeConstrainedToEnumeratedValue() {
        return (question.getConstraints() instanceof MultiValueConstraints);
    }
    
    private boolean allowsMultipleAnswers() {
        return maybeConstrainedToEnumeratedValue() && ((MultiValueConstraints)question.getConstraints()).getAllowMultiple();
    }
    
    private boolean hasNoAnswer(SurveyAnswer answer) {
        return BridgeUtils.isEmpty(answer.getAnswers());
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
        if (con.getEarliestValue() != null && time < con.getEarliestValue()) {
            rejectField(errors, "constraints", "%s is not allowed to have a date before %s", time, con.getEarliestValue());
        }
        if (con.getLatestValue() != null && time > con.getLatestValue()) {
            rejectField(errors, "constraints", "%s is not allowed to have a date after %s", time, con.getLatestValue());
        }
    }
    
    private void validateType(Errors errors, NumericalConstraints con, String answer, String typeName) {
        try {
            if (answer != null) {
                String unitString = (con.getUnit() != null) ? (" " + con.getUnit().name().toLowerCase()) : "";
                double value = Double.parseDouble(answer);
                if (con.getMinValue() != null && value < con.getMinValue()) {
                    rejectField(errors, "constraints", "%s is lower than the minimum value of %s%s", answer, con.getMinValue(), unitString);
                }
                if (con.getMaxValue() != null && value > con.getMaxValue()) {
                    rejectField(errors, "constraints", "%s is higher than the maximum value of %s%s", answer, con.getMaxValue(), unitString);
                }
                if (con.getStep() != null) {
                    double delta = value % con.getStep();
                    if (delta > (con.getStep()/10)) {
                        rejectField(errors, "constraints", "%s is not a step value of %s", answer, con.getStep());
                    }
                }
            }
        } catch(NumberFormatException e) {
            rejectField(errors, "constraints", "%s is not a valid %s", answer, typeName);
        }
    }

    private void validateType(Errors errors, DurationConstraints con, String answer) {
        if (con.getUnit() == null) {
            rejectField(errors, "constraints", "unit is required", answer);
        } else if (!Unit.DURATION_UNITS.contains(con.getUnit())) {
            rejectField(errors, "constraints", "%s is not a time unit", con.getUnit().name().toLowerCase());
        } else {
            try {
                String value = new DurationToIntegerConverter().convert(answer, con.getUnit());
                validateType(errors, (NumericalConstraints)con, value, "integer");
            } catch(IllegalArgumentException e) {
                rejectField(errors, "constraints", e.getMessage());
            }
        }
    }
    
    private void validateType(Errors errors, StringConstraints con, String answer) {
        if (con.getMinLength() != null && answer.length() < con.getMinLength()) {
            rejectField(errors, "constraints", "%s is shorter than %s characters", answer, con.getMinLength());
        } else if (con.getMaxLength() != null && answer.length() > con.getMaxLength()) {
            rejectField(errors, "constraints", "%s is longer than %s characters", answer, con.getMaxLength());
        }
        if (StringUtils.isNotBlank(con.getPattern()) && answer != null && !answer.matches(con.getPattern())) {
            rejectField(errors, "constraints", "%s does not match the regular expression /%s/", answer, con.getPattern());
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
            validateType(errors, INTEGER_CONSTRAINTS, answer, "integer");
            break;
        case DECIMAL:
            validateType(errors, DECIMAL_CONSTRAINTS, answer, "decimal");
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
