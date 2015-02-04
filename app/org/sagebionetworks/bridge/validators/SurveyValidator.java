package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_QUESTION_TYPE;
import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_INFO_SCREEN_TYPE;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.google.common.collect.Sets;

public class SurveyValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return Survey.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Survey survey = (Survey)object;
        if (StringUtils.isBlank(survey.getIdentifier())) {
            errors.reject("missing an identifier");
        }
        if (StringUtils.isBlank(survey.getStudyIdentifier())) {
            errors.reject("missing a study key");
        }
        if (StringUtils.isBlank(survey.getGuid())) {
            errors.reject("missing a GUID");
        }
        for (int i=0; i < survey.getElements().size(); i++) {
            SurveyElement element = survey.getElements().get(i);
            errors.pushNestedPath("element"+i);
            if (SURVEY_QUESTION_TYPE.equals(element.getType())) {
                doValidateQuestion((SurveyQuestion)element, i, errors);    
            } else if (SURVEY_INFO_SCREEN_TYPE.equals(element.getType())) {
                doValidateInfoScreen((SurveyInfoScreen)element, i, errors);
            }
            errors.popNestedPath();
            
        }
        // You can get all sorts of NPEs if survey is not valid and you look at the rules.
        // So don't.
        if (!errors.hasErrors()) {
            List<SurveyQuestion> questions = survey.getUnmodifiableQuestionList();
            validateRules(errors, questions);    
        }
    }
    private void doValidateQuestion(SurveyQuestion question, int pos, Errors errors) {
        if (isBlank(question.getIdentifier())) {
            errors.rejectValue("identifier", "is required");
        }
        if (question.getUiHint() == null) {
            errors.rejectValue("uiHint", "is required");
        }
        if (isBlank(question.getPrompt())) {
            errors.rejectValue("prompt", "is required");
        }
        if (question.getConstraints() == null) {
            errors.rejectValue("constraints", "is required");
        } else {
            errors.pushNestedPath("constraints");
            doValidateConstraints(question, question.getConstraints(), errors);
            errors.popNestedPath();
        }
    }
    private void doValidateInfoScreen(SurveyInfoScreen screen, int i, Errors errors) {
        if (isBlank(screen.getIdentifier())) {
            errors.rejectValue("identifier", "is required");
        }
        if (isBlank(screen.getTitle())) {
            errors.rejectValue("title", "is required");
        }
        if (isBlank(screen.getPrompt())) {
            errors.rejectValue("prompt", "is required");
        }
        if (isNotBlank(screen.getImageSource()) && 
            !screen.getImageSource().startsWith("http://") && 
            !screen.getImageSource().startsWith("https://")) {
            errors.rejectValue("imageSource", "must be a valid URL to an image");
        }
    }
    private void validateRules(Errors errors, List<SurveyQuestion> questions) {
        // Should not try and back-track in the survey.
        Set<String> alreadySeenIdentifiers = Sets.newHashSet();
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            for (SurveyRule rule : question.getConstraints().getRules()) {
                if (alreadySeenIdentifiers.contains(rule.getSkipToTarget())) {
                    errors.pushNestedPath("question"+i);
                    rejectField(errors, "rule", "back references question %s", rule.getSkipToTarget());
                    errors.popNestedPath();
                }
            }
            alreadySeenIdentifiers.add(question.getIdentifier());
        }
        // Now verify that all skipToTarget identifiers actually exist
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            for (SurveyRule rule : question.getConstraints().getRules()) {
                if (!alreadySeenIdentifiers.contains(rule.getSkipToTarget())) {
                    errors.pushNestedPath("question"+i);
                    rejectField(errors, "rule", "has a skipTo identifier that doesn't exist: %s", rule.getSkipToTarget());
                    errors.popNestedPath();
                }
            }
        }
        
    }
    private void doValidateConstraints(SurveyQuestion question, Constraints con, Errors errors) {
        if (con.getDataType() == null) {
            errors.reject("has no dataType");
            return;
        }
        UIHint hint = question.getUiHint();
        if (hint == null) {
            return; // will have been validated above, skip this
        }
        if (!con.getSupportedHints().contains(hint)) {
            rejectField(errors, "dataType", "(%s) doesn't match the UI hint of '%s'", con.getDataType().name()
                    .toLowerCase(), hint.name().toLowerCase());
        } else if (con instanceof MultiValueConstraints) {
            // Multiple values have a few odd UI constraints
            MultiValueConstraints mcon = (MultiValueConstraints)con;
            String hintName = hint.name().toLowerCase();
            
            if (hint == UIHint.COMBOBOX && (mcon.getAllowMultiple() || !mcon.getAllowOther())) {
                rejectField(errors, "uiHint", "'%s' is only valid when multiple = false and other = true", hintName);
            } else if (mcon.getAllowMultiple() && hint != UIHint.CHECKBOX && hint != UIHint.LIST) {
                rejectField(errors, "uiHint",
                        "allows multiples but the '%s' UI hint doesn't gather more than one answer", hintName);
            } else if (!mcon.getAllowMultiple() && (hint == UIHint.CHECKBOX || hint == UIHint.LIST)) {
                rejectField(errors, "uiHint",
                        "doesn't allow multiples but the '%s' UI hint gathers more than one answer", hintName);
            }
        } else if (con instanceof StringConstraints) {
            // Validate the regular expression, if it exists
            StringConstraints scon = (StringConstraints)con;
            if (StringUtils.isNotBlank(scon.getPattern())) {
                try {
                    Pattern.compile(scon.getPattern());
                } catch (PatternSyntaxException exception) {
                    rejectField(errors, "pattern", "is not a valid regular expression: %s", scon.getPattern());
                }
            }
        }
    }
    private void rejectField(Errors errors, String field, String message, Object... args) {
        if (args != null && args.length > 0) {
            errors.rejectValue(field, message, args, message);    
        } else {
            errors.rejectValue(field, message);
        }
    }
}
