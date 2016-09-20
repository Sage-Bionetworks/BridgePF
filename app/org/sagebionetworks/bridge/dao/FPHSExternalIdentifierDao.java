package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

public interface FPHSExternalIdentifierDao {

    void verifyExternalId(ExternalIdentifier externalId);
    
    void registerExternalId(ExternalIdentifier externalId);
    
    void unregisterExternalId(ExternalIdentifier externalId);
    
    List<FPHSExternalIdentifier> getExternalIds();
    
    void addExternalIds(List<FPHSExternalIdentifier> externalIds);
    
    void deleteExternalId(String externalId);

}
