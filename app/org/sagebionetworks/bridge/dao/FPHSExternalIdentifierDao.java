package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

public interface FPHSExternalIdentifierDao {

    public boolean verifyExternalId(ExternalIdentifier externalId);
    
    public void registerExternalId(ExternalIdentifier externalId);
    
    public List<FPHSExternalIdentifier> getExternalIds();
    
    public void updateExternalIds(List<FPHSExternalIdentifier> externalIds);

}
