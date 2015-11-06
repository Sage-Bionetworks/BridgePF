package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;

@Component
public class DynamoFPHSExternalIdentifierDao implements FPHSExternalIdentifierDao {

    private DynamoDBMapper mapper;
    
    @Resource(name = "fphsExternalIdDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public boolean verifyExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        DynamoFPHSExternalIdentifier record = getExternalId(externalId, false);
        return (record != null && !record.getRegistered());
    }

    @Override
    public void registerExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        DynamoFPHSExternalIdentifier record = getExternalId(externalId, true);
        record.setRegistered(true);
        mapper.save(record);
    }
    
    @Override
    public void unregisterExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        DynamoFPHSExternalIdentifier record = getExternalId(externalId, true);
        record.setRegistered(false);
        mapper.save(record);
    }

    @Override
    public List<FPHSExternalIdentifier> getExternalIds() {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        
        return mapper.scan(DynamoFPHSExternalIdentifier.class, scan).stream().map(identifier -> {
            return (FPHSExternalIdentifier)identifier;
        }).collect(Collectors.toList());
    }

    @Override
    public void addExternalIds(List<FPHSExternalIdentifier> externalIds) {
        checkNotNull(externalIds);
        
        if (!externalIds.isEmpty()) {
            List<DynamoFPHSExternalIdentifier> idsToSave = externalIds.stream().filter(id -> {
                return mapper.load(id) == null;
            }).map(id -> {
                return new DynamoFPHSExternalIdentifier(id.getExternalId());  
            }).collect(Collectors.toList());
            if (!idsToSave.isEmpty()) {
                List<FailedBatch> failures = mapper.batchSave(idsToSave);
                BridgeUtils.ifFailuresThrowException(failures);
            }
        }
    }
    
    @Override
    public void deleteAll() {
        List<DynamoFPHSExternalIdentifier> identifiers = getExternalIds().stream().map(id -> {
            return new DynamoFPHSExternalIdentifier(id.getExternalId());
        }).collect(Collectors.toList());

        if (!identifiers.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(identifiers);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    private DynamoFPHSExternalIdentifier getExternalId(ExternalIdentifier externalId, boolean throwExceptions) {
        DynamoFPHSExternalIdentifier hashKey = new DynamoFPHSExternalIdentifier();
        hashKey.setExternalId(externalId.getIdentifier());
        
        DynamoFPHSExternalIdentifier record = mapper.load(hashKey);
        if (throwExceptions) {
            if (record == null) {
                throw new EntityNotFoundException(FPHSExternalIdentifier.class);
            }
            if (record.getRegistered()) {
                throw new EntityAlreadyExistsException(record);
            }
        }
        return record;
    }

}
