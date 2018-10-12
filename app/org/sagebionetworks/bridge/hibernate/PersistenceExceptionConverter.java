package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

public interface PersistenceExceptionConverter {
    /**
     * Return a domain-specific exception for this persistence exception. If the converter 
     * does not wish to convert an exception, it should return the original persistence
     * exception.
     *   
     * @param exception
     *      the persistence exception thrown by Hibernate
     * @param entity
     *      the entity passed to HibernateHelper
     * @return
     *      return new exception if converted, or the original exception if no conversion is to take place.
     */
    RuntimeException convert(PersistenceException exception, Object entity);
}
