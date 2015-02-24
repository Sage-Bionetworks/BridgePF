package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.UUID;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.HealthIdImpl;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCodeServiceImpl implements HealthCodeService {

    private final Logger logger = LoggerFactory.getLogger(HealthCodeServiceImpl.class);

    private HealthIdDao healthIdDao;
    private HealthCodeDao healthCodeDao;

    public void setHealthIdDao(HealthIdDao healthIdDao) {
        this.healthIdDao = healthIdDao;
    }
    public void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    @Override
    public HealthId createMapping(StudyIdentifier studyIdentifier) {
        checkNotNull(studyIdentifier);
        final String healthCode = generateHealthCode(studyIdentifier.getIdentifier());
        final String healthId = generateHealthId(healthCode);
        return new HealthIdImpl(healthId, healthCode);
    }

    @Override
    public HealthId getMapping(String healthId) {
        if (healthId == null) {
            return null;
        }
        final String healthCode = healthIdDao.getCode(healthId);
        if (healthCode == null) {
            return null;
        }
        return new HealthIdImpl(healthId, healthCode);
    }
    
    private String generateHealthCode(String studyId) {
        String code = UUID.randomUUID().toString();
        boolean isSet = healthCodeDao.setIfNotExist(code, studyId);
        while (!isSet) {
            logger.error("Health code " + code + " conflicts. This should never happen. " +
                    "Make sure the UUID generator is a solid one.");
            code = UUID.randomUUID().toString();
            isSet = healthCodeDao.setIfNotExist(code, studyId);
        }
        return code;
    }

    private String generateHealthId(final String code) {
        String id = UUID.randomUUID().toString();
        boolean isSet = healthIdDao.setIfNotExist(id, code);
        while (!isSet) {
            logger.error("Health ID " + id + " conflicts. This should never happen. " +
                    "Make sure the UUID generator is a solid one.");
            id = UUID.randomUUID().toString();
            isSet = healthIdDao.setIfNotExist(id, code);
        }
        return id;
    }
}

