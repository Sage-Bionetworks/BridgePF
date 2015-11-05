package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

@Component
public class DynamoFPHSExternalIdentifierDao implements FPHSExternalIdentifierDao {

    private DynamoDBMapper mapper;

    @Resource(name = "fphsExternalIdDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public boolean verifyExternalId(ExternalIdentifier externalId) {
        return getExternalId(externalId) != null;
    }

    @Override
    public void registerExternalId(ExternalIdentifier externalId) {
        // Should never be null as that path leads to an exception being thrown
        FPHSExternalIdentifier record = getExternalId(externalId);
        record.setRegistered(true);
        mapper.save(record);
    }

    @Override
    public List<FPHSExternalIdentifier> getExternalIds() {
        
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateExternalIds(List<FPHSExternalIdentifier> externalIds) {
        // TODO Auto-generated method stub

    }
    
    private FPHSExternalIdentifier getExternalId(ExternalIdentifier externalId) {
        FPHSExternalIdentifier hashKey = new DynamoFPHSExternalIdentifier();
        hashKey.setExternalId(externalId.getIdentifier());
        
        FPHSExternalIdentifier record = mapper.load(hashKey);
        if (record == null) {
            throw new EntityNotFoundException(FPHSExternalIdentifier.class);
        }
        if (record.getRegistered()) {
            throw new EntityAlreadyExistsException(record);
        }
        return record;
    }

}
