package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;

import static play.mvc.Http.Status.*;

public class StudyConsentServiceImpl implements StudyConsentService {

    private StudyConsentDao studyConsentDao;

    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    @Override
    public StudyConsent addConsent(User caller, String studyKey, String path, int minAge) {
        if (!caller.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to add consent document.", FORBIDDEN);
        }

        StudyConsent studyConsent = studyConsentDao.getConsent(studyKey);
        if (studyConsent != null) {
            studyConsentDao.setActive(studyConsent, false);
        }

        studyConsent = studyConsentDao.addConsent(studyKey, path, minAge);
        studyConsentDao.setActive(studyConsent, true);

        return studyConsentDao.getConsent(studyKey);
    }

    @Override
    public StudyConsent getActiveConsent(User caller, String studyKey) {
        if (!caller.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to get consent document.", FORBIDDEN);
        }

        StudyConsent consent = studyConsentDao.getConsent(studyKey);
        if (consent == null) {
            throw new BridgeServiceException("There is no active consent document.", BAD_REQUEST);
        }
        return consent;
    }

    @Override
    public List<StudyConsent> getAllConsents(User caller, String studyKey) {
        if (!caller.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to get consent document.", FORBIDDEN);
        }
        return studyConsentDao.getConsents(studyKey);
    }

    @Override
    public StudyConsent getConsent(User caller, String studyKey, long timestamp) {
        if (!caller.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to get consent document.", FORBIDDEN);
        }
        return studyConsentDao.getConsent(studyKey, timestamp);
    }

    @Override
    public void activateConsent(User caller, String studyKey, long timestamp) {
        if (!caller.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", FORBIDDEN);
        }

        StudyConsent studyConsent = studyConsentDao.getConsent(studyKey);
        if (studyConsent != null) {
            studyConsentDao.setActive(studyConsent, false);
        }

        studyConsent = studyConsentDao.getConsent(studyKey, timestamp);
        studyConsentDao.setActive(studyConsent, true);
    }

    @Override
    public void deleteConsent(User caller, String studyKey, long timestamp) {
        if (!caller.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", FORBIDDEN);
        }
        if (studyConsentDao.getConsent(studyKey, timestamp).getActive()) {
            throw new BridgeServiceException("Cannot delete active consent document.", BAD_REQUEST);
        }

        studyConsentDao.deleteConsent(studyKey, timestamp);
    }

}
