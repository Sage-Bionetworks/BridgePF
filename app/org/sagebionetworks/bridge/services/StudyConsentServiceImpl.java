package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;

public class StudyConsentServiceImpl implements StudyConsentService {

    private Validator validator;
    private StudyConsentDao studyConsentDao;

    public void setValidator(Validator validator) {
        this.validator = validator;
    }
    
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    
    @Override
    public StudyConsent addConsent(StudyIdentifier studyIdentifier, StudyConsentForm form) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkNotNull(form, "Study consent is null");
        
        Validate.entityThrowingException(validator, form);
        return studyConsentDao.addConsent(studyIdentifier, form.getPath(), form.getMinAge());
    }

    @Override
    public StudyConsent getActiveConsent(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        
        StudyConsent consent = studyConsentDao.getConsent(studyIdentifier);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return consent;
    }

    @Override
    public List<StudyConsent> getAllConsents(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        
        List<StudyConsent> consents = studyConsentDao.getConsents(studyIdentifier);
        if (consents == null || consents.isEmpty()) {
            throw new BadRequestException("There are no consent records.");
        }
        return consents;
    }

    @Override
    public StudyConsent getConsent(StudyIdentifier studyIdentifier, long timestamp) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(studyIdentifier, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return consent;
    }

    @Override
    public StudyConsent activateConsent(StudyIdentifier studyIdentifier, long timestamp) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(studyIdentifier, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return studyConsentDao.setActive(consent, true);
    }

    @Override
    public void deleteConsent(StudyIdentifier studyIdentifier, long timestamp) {
        checkNotNull(studyIdentifier, "StudyIdentifier is null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        if (studyConsentDao.getConsent(studyIdentifier, timestamp).getActive()) {
            throw new BadRequestException("Cannot delete active consent document.");
        }
        studyConsentDao.deleteConsent(studyIdentifier, timestamp);
    }
}
