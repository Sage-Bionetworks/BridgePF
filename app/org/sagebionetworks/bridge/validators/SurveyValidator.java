package org.sagebionetworks.bridge.validators;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.google.common.collect.Sets;

public class SurveyValidator implements Validator<Survey> {
    
    @Override
    public void validateNew(Survey survey) throws InvalidEntityException, EntityAlreadyExistsException {
        Messages messages = new Messages();
        if (StringUtils.isNotBlank(survey.getGuid())) {
            messages.add("should not have a GUID");
        } else if (survey.isPublished()) {
            messages.add("should not be marked as published");
        } else if (survey.getVersion() != null) {
            messages.add("should not have a lock version number");
        } else if (survey.getVersionedOn() != 0L) {
            messages.add("should not have a versionedOn date");
        }
        for (int i=0; i < survey.getQuestions().size(); i++) {
            SurveyQuestion question = survey.getQuestions().get(i);
            if (StringUtils.isNotBlank(question.getGuid())) {
                messages.add("question #%s should not have a GUID", i);
            }
        }
        if (!messages.isEmpty()) {
            throw new EntityAlreadyExistsException(survey, "Survey does not appear to be new: " + messages.join());
        }
        doValidation(survey, true);
    }

    @Override
    public void validate(Survey survey) throws InvalidEntityException {
        doValidation(survey, false);
    }

    private void doValidation(Survey survey, boolean isNew) throws InvalidEntityException {
        Messages messages = new Messages();
        if (StringUtils.isBlank(survey.getIdentifier())) {
            messages.add("missing an identifier");
        } else if (StringUtils.isBlank(survey.getStudyKey())) {
            messages.add("missing a study key");
        }
        if (!isNew && StringUtils.isBlank(survey.getGuid())) {
            messages.add("missing a GUID");
        }
        for (int i=0; i < survey.getQuestions().size(); i++) {
            SurveyQuestion question = survey.getQuestions().get(i);
            doValidateQuestion(question, isNew, i, messages);
        }
        validateConstraintRules(messages, survey.getQuestions());
        if (!messages.isEmpty()) {
            throw new InvalidEntityException(survey, "Survey is not valid: " + messages.join());
        }
    }
    
    private void doValidateQuestion(SurveyQuestion question, boolean isNew, int pos, Messages messages) {
        if (!isNew && StringUtils.isBlank(question.getGuid())) {
            messages.add("question #%s is missing a GUID", pos);
        }
        if (isNew && StringUtils.isBlank(question.getIdentifier())) {
            messages.add("question #%s is missing an identifier", pos);
        }
        if (question.getConstraints() == null) {
            messages.add("question #%s is missing constraints, including the data type of the question", pos);
        }
        if (question.getUiHint() == null) {
            messages.add("question #%s is missing a UI hint", pos);
        }
        if (StringUtils.isBlank(question.getPrompt())) {
            messages.add("question #%s is missing a prompt/question text for the user", pos);
        }
        // Stop here if basic stuff is missing.
        if (!messages.isEmpty()) {
            return;
        }
        Constraints con = question.getConstraints();
        UIHint hint = question.getUiHint();
        if (!con.getSupportedHints().contains(hint)) {
            messages.add("question #%s has a data type of '%s' that doesn't match the UI hint of %s", pos,
                    con.getDataType(), hint.name().toLowerCase());
        } else if (con instanceof MultiValueConstraints) {
            // Multiple values have a few odd UI constraints
            MultiValueConstraints mcon = (MultiValueConstraints)con;
            if (hint == UIHint.COMBOBOX && (mcon.getAllowMultiple() || !mcon.getAllowOther())) {
                messages.add("question #%s asks for combobox but that's only valid when multiple = false and other = true", pos);
            } else if (mcon.getAllowMultiple() && hint != UIHint.CHECKBOX && hint != UIHint.LIST) {
                messages.add("question #%s allows multiples but %s doesn't gather more than one answer", pos, hint.name().toLowerCase());
            } else if (!mcon.getAllowMultiple() && (hint == UIHint.CHECKBOX || hint == UIHint.LIST)) {
                messages.add("question #%s doesn't allow multiples but %s gathers more than one answer", pos, hint.name().toLowerCase());
            }
        } else if (con instanceof StringConstraints) {
            // Validate the regular expression, if it exists
            StringConstraints scon = (StringConstraints)con;
            if (StringUtils.isNotBlank(scon.getPattern())) {
                try {
                    Pattern.compile(scon.getPattern());
                } catch (PatternSyntaxException exception) {
                    messages.add("Pattern is not a valid regular expression: " + scon.getPattern());
                }
            }
        }
    }
    
    private void validateConstraintRules(Messages messages, List<SurveyQuestion> questions) {
        // Should not try and back-track in the survey.
        Set<String> alreadySeenIdentifiers = Sets.newHashSet();
        for (int i=0; i < questions.size(); i++) {
            SurveyQuestion question = questions.get(i);
            for (SurveyRule rule : question.getConstraints().getRules()) {
                if (alreadySeenIdentifiers.contains(rule.getGotoTarget())) {
                    messages.add("question #%s has a rule that back references question %s: %s", i, rule.getGotoTarget(), rule.toString());
                }
            }
            alreadySeenIdentifiers.add(question.getIdentifier());
        }
    }

}
