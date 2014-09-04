package org.sagebionetworks.bridge.services;

import static play.mvc.Http.Status.BAD_REQUEST;

import java.util.List;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;

public class StudyConsentServiceImpl implements StudyConsentService {

    private StudyConsentDao studyConsentDao;

    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    @Override
    public StudyConsent addConsent(String studyKey, StudyConsentForm form) {
        return studyConsentDao.addConsent(studyKey, form.getPath(), form.getMinAge());
    }

    @Override
    public StudyConsent getActiveConsent(String studyKey) {
        StudyConsent consent = studyConsentDao.getConsent(studyKey);
        if (consent == null) {
            throw new BridgeServiceException("There is no active consent document.", BAD_REQUEST);
        }
        return consent;
    }

    @Override
    public List<StudyConsent> getAllConsents(String studyKey) {
        List<StudyConsent> consents = studyConsentDao.getConsents(studyKey);
        if (consents == null || consents.isEmpty()) {
            throw new BridgeServiceException("There are no consent records.", BAD_REQUEST);
        }
        return consents;
    }

    @Override
    public StudyConsent getConsent(String studyKey, long timestamp) {
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);
        if (consent == null) {
            throw new BridgeServiceException("No consent with that timestamp exists.", BAD_REQUEST);
        }
        return consent;
    }

    @Override
    public StudyConsent activateConsent(String studyKey, long timestamp) {
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);
        if (consent == null) {
            throw new BridgeServiceException("No consent with that timestamp exists.", BAD_REQUEST);
        }
        return studyConsentDao.setActive(consent, true);
    }

    @Override
    public void deleteConsent(String studyKey, long timestamp) {
        if (studyConsentDao.getConsent(studyKey, timestamp).getActive()) {
            throw new BridgeServiceException("Cannot delete active consent document.", BAD_REQUEST);
        }
        studyConsentDao.deleteConsent(studyKey, timestamp);
    }
}
