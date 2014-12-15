package org.sagebionetworks.bridge.validators;

import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ConsentAgeValidator implements Validator {

    private final Study study;
    
    public ConsentAgeValidator(Study study) {
        this.study = study;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return ConsentSignature.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ConsentSignature sig = (ConsentSignature)object;

        if (study.getMinAgeOfConsent() == 0) {
            return;
        }
        LocalDate birthdate = LocalDate.parse(sig.getBirthdate());
        LocalDate now = LocalDate.now();
        Period period = new Period(birthdate, now);

        if (period.getYears() < study.getMinAgeOfConsent()) {
            String message = String.format("too recent (the study requires participants to be %s years of age or older).", study.getMinAgeOfConsent());
            errors.rejectValue("birthdate", message);
        }
    }

}
