package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;

import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

/**
 */
public class FPHSService {
    
    private FPHSExternalIdentifierDao fphsDao;
    private ParticipantOptionsService optionsService;
    
    @Autowired
    public final void setFPHSExternalIdentifierDao(FPHSExternalIdentifierDao dao) {
        this.fphsDao = dao;
    }
    @Autowired
    public final void setParticipantOptionsService(ParticipantOptionsService options) {
        this.optionsService = options;
    }
    
    public boolean verifyExternalIdentifier(ExternalIdentifier externalId) throws Exception {
        if (isBlank(externalId.getIdentifier())) {
            throw new InvalidEntityException(externalId);
        }
        return fphsDao.verifyExternalId(externalId);
    }
    public void registerExternalIdentifier(StudyIdentifier studyId, String healthCode, ExternalIdentifier externalId) throws Exception {
        if (isBlank(externalId.getIdentifier())) {
            throw new InvalidEntityException(externalId);
        }
        // Throws exception if the identifier is used or not found, preventing optionsService from being called.
        fphsDao.registerExternalId(externalId);
        // TODO: Compensate if this fails?
        optionsService.setOption(studyId, healthCode, ParticipantOption.EXTERNAL_IDENTIFIER, externalId.getIdentifier());
    }
    public List<FPHSExternalIdentifier> getExternalIdentifiers() throws Exception {
        return fphsDao.getExternalIds();
    }
    public void updateExternalIdentifiers(List<FPHSExternalIdentifier> externalIds) throws Exception {
        fphsDao.updateExternalIds(externalIds);
    }
}
