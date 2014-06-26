package org.sagebionetworks.bridge.services;

import java.util.UUID;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.models.HealthId;
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
    public HealthId create() {
        final String healthCode = generateHealthCode();
        final String healthId = generateHealthId(healthCode);
        return new HealthId() {
            @Override
            public String getId() {
                return healthId;
            }
            @Override
            public String getCode() {
                return healthCode;
            }
        };
    }

    @Override
    public String getHealthCode(String id) {
        return healthIdDao.getCode(id);
    }

    private String generateHealthCode() {
        String code = UUID.randomUUID().toString();
        boolean isSet = healthCodeDao.setIfNotExist(code);
        while (!isSet) {
            logger.warn("Health code " + code + " conflicts. This should never happen. " +
                    "Make sure the UUID generator is a solid one.");
            code = UUID.randomUUID().toString();
            isSet = healthCodeDao.setIfNotExist(code);
        }
        return code;
    }

    private String generateHealthId(final String code) {
        String id = UUID.randomUUID().toString();
        boolean isSet = healthIdDao.setIfNotExist(id, code);
        while (!isSet) {
            logger.warn("Health ID " + id + " conflicts. This should never happen. " +
                    "Make sure the UUID generator is a solid one.");
            id = UUID.randomUUID().toString();
            isSet = healthIdDao.setIfNotExist(id, code);
        }
        return id;
    }
}
