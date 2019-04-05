package org.sagebionetworks.bridge.hibernate;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.persistence.PersistenceException;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

/** Encapsulates common scenarios for using Hibernate to make it easier to use. */
public class HibernateHelper {
    private SessionFactory hibernateSessionFactory;
    private PersistenceExceptionConverter exceptionConverter;

    public HibernateHelper(SessionFactory hibernateSessionFactory, PersistenceExceptionConverter exceptionConverter) {
        this.hibernateSessionFactory = hibernateSessionFactory;
        this.exceptionConverter = exceptionConverter;
    }

    /**
     * Creates (inserts) an object through Hibernate. Throws a ConcurrentModificationException if creating the object
     * would violate a key constraint, most commonly if the row already exists. A consumer may be passed to this method 
     * that will receive the object in the context of a database transaction; if the consumer throws a runtime error, 
     * the transaction will be aborted.
     */
    public <T> void create(T obj, Consumer<T> consumer) {
        executeWithExceptionHandling(obj, session -> {
            session.save(obj);
            if (consumer != null) {
                consumer.accept(obj); // if this throws, changes to account are abandoned    
            }
            return obj;
        });
    }

    /** Deletes the given object. */
    public <T> void deleteById(Class<T> clazz, Serializable id) {
        // Hibernate optimistic versioning also applies to deletes. However, unlike updates, when we delete something,
        // we want it gone, so we generally don't care about optimistic versioning. In order to handle this in
        // Hibernate, we need to load the whole object before deleting it.
        executeWithExceptionHandling(null, session -> {
            T obj = session.get(clazz, id);
            session.delete(obj);
            return null;
        });
    }

    /** Get by the table's primary key. Returns null if the object doesn't exist. */
    public <T> T getById(Class<T> clazz, Serializable id) {
        return executeWithExceptionHandling(null, session -> session.get(clazz, id));
    }

    /**
     * Executes the query and returns the count. The query should be a count based query.
     */
    public int queryCount(String queryString, Map<String,Object> parameters) {
        // Hibernate returns a long for a count. However, we never expect more than 2 billion rows, for obvious
        // reasons.
        Long count = executeWithExceptionHandling(null, session -> {
            Query<Long> query = session.createQuery(queryString, Long.class);
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    query.setParameter(entry.getKey(), entry.getValue());
                }
            }
            return query.uniqueResult();
        });
        if (count != null) {
            return count.intValue();
        } else {
            // This is unusual, but to protect from NPEs, return 0.
            return 0;
        }
    }

    /**
     * Executes the query and returns a list of results. Returns an empty list if there's no result. Optional offset
     * and limit for pagination.
     */
    public <T> List<T> queryGet(String queryString, Map<String,Object> parameters, Integer offset, Integer limit, Class<T> clazz) {
        return executeWithExceptionHandling(null, session -> {
            Query<T> query = session.createQuery(queryString, clazz);
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    query.setParameter(entry.getKey(), entry.getValue());
                }
            }
            if (offset != null) {
                query.setFirstResult(offset);
            }
            if (limit != null) {
                query.setMaxResults(limit);
            }
            return query.list();
        });
    }

    /**
     * Executes the given query as an update. Can either be an UPDATE query or a DELETE query. Returns the number of
     * rows affected by this query.
     */
    public int queryUpdate(String queryString, Map<String,Object> parameters) {
        return executeWithExceptionHandling(null, session -> { 
            Query<?> query = session.createQuery(queryString);
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    query.setParameter(entry.getKey(), entry.getValue());
                }
            }
            return query.executeUpdate();
        });
    }
    
    /**
     * Execute SQL query with no return value, like a batch delete. 
     */
    public void query(String queryString, Map<String,Object> parameters) {
        executeWithExceptionHandling(null, session -> { 
            Query<?> query = session.createQuery(queryString);
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    query.setParameter(entry.getKey(), entry.getValue());
                }
            }
            return query.executeUpdate();
        });
    }

    /** Updates a single object. */
    public <T> T update(T obj, Consumer<T> afterPersistConsumer) {
        return executeWithExceptionHandling(obj, session -> {
            session.update(obj);
            if (afterPersistConsumer != null) {
                afterPersistConsumer.accept(obj); // if this throws, changes to account are abandoned    
            }
            return obj;
        });
    }
    
    <T> T executeWithExceptionHandling(T originalEntity, Function<Session, T> function) {
        try {
            return execute(function);
        } catch(PersistenceException pe) {
            RuntimeException ex = exceptionConverter.convert(pe, originalEntity);
            if (ex == pe) {
                throw new BridgeServiceException(ex);
            } else {
                throw ex;
            }
        }
    }

    // Helper function, which handles opening and closing sessions and transactions.
    // Package-scoped to facilitate unit tests.
    <T> T execute(Function<Session, T> function) {
        T retval;
        try (Session session = hibernateSessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            retval = function.apply(session);
            transaction.commit();
        }
        return retval;
    }
}
