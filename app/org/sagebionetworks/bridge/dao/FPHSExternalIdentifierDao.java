package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

public interface FPHSExternalIdentifierDao {

    public void verifyExternalId(ExternalIdentifier externalId);
    
    public void registerExternalId(ExternalIdentifier externalId);
    
    public void unregisterExternalId(ExternalIdentifier externalId);
    
    public List<FPHSExternalIdentifier> getExternalIds();
    
    public void addExternalIds(List<FPHSExternalIdentifier> externalIds);
    
    public void deleteExternalId(String externalId);

}
