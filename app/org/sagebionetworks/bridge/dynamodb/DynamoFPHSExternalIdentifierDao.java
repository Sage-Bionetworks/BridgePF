package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;

@Component
public class DynamoFPHSExternalIdentifierDao implements FPHSExternalIdentifierDao {
    private static final String CONFIG_KEY_ADD_LIMIT = "fphs.id.add.limit";

    private int addLimit;
    private DynamoDBMapper mapper;

    /** Gets the add limit from Config. */
    @Autowired
    public final void setConfig(Config config) {
        addLimit = config.getInt(CONFIG_KEY_ADD_LIMIT);
    }
    
    @Resource(name = "fphsExternalIdDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public void verifyExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        // This will throw an exception if the record doesn't exist or has already been registered.
        getExternalId(externalId, true);
    }

    @Override
    public void registerExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        // This will throw an exception if the record doesn't exist or has already been registered.
        DynamoFPHSExternalIdentifier record = getExternalId(externalId, true);
        record.setRegistered(true);
        mapper.save(record);
    }
    
    @Override
    public void unregisterExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        DynamoFPHSExternalIdentifier record = getExternalId(externalId, false);
        if (record != null) {
            record.setRegistered(false);
            mapper.save(record);
        }
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

        // If the list is too large, this will hit DDB throttling limits and also take forever.
        // If the list is too large, throw a BadRequestException.
        if (externalIds.size() > addLimit) {
            throw new BadRequestException("ID list too large; size=" + externalIds.size() + ", limit=" + addLimit);
        }

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
    public void deleteExternalId(String identifier) {
        DynamoFPHSExternalIdentifier externalId = new DynamoFPHSExternalIdentifier(identifier);
        mapper.delete(externalId);
    }
    
    private DynamoFPHSExternalIdentifier getExternalId(ExternalIdentifier externalId, boolean throwExceptions) {
        DynamoFPHSExternalIdentifier hashKey = new DynamoFPHSExternalIdentifier();
        hashKey.setExternalId(externalId.getExternalId());
        
        DynamoFPHSExternalIdentifier record = mapper.load(hashKey);
        if (throwExceptions) {
            if (record == null) {
                throw new EntityNotFoundException(FPHSExternalIdentifier.class);
            }
            if (record.isRegistered()) {
                throw new EntityAlreadyExistsException(record);
            }
        }
        return record;
    }

}
