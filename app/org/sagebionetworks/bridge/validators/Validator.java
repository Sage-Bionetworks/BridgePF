package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

public interface Validator<T> {

    public void validateNew(T object) throws InvalidEntityException, EntityAlreadyExistsException;
    
    public void validate(T object) throws InvalidEntityException;

}
