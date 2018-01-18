package org.sagebionetworks.bridge.hibernate;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

/** Encapsulates common scenarios for using Hibernate to make it easier to use. */
@Component
public class HibernateHelper {
    private SessionFactory hibernateSessionFactory;

    /** Hibernate session factory, used to talk to SQL. Configured via Spring. */
    @Autowired
    public final void setHibernateSessionFactory(SessionFactory hibernateSessionFactory) {
        this.hibernateSessionFactory = hibernateSessionFactory;
    }

    /**
     * Creates (inserts) an object through Hibernate. Throws a ConcurrentModificationException if creating the object
     * would violate a key constraint, most commonly if the row already exists.
     */
    public void create(Object obj) {
        try {
            execute(session -> session.save(obj));
        } catch (PersistenceException ex) {
            // If you try to create a row that already exists, Hibernate will throw a PersistenceException wrapped in a
            // ConstraintViolationException.
            if (ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                throw new ConcurrentModificationException(
                        "Attempting to write a new row that violates key constraints");
            } else {
                throw ex;
            }
        }
    }

    /** Deletes the given object. */
    public <T> void deleteById(Class<T> clazz, Serializable id) {
        // Hibernate optimistic versioning also applies to deletes. However, unlike updates, when we delete something,
        // we want it gone, so we generally don't care about optimistic versioning. In order to handle this in
        // Hibernate, we need to load the whole object before deleting it.
        execute(session -> {
            T obj = session.get(clazz, id);
            session.delete(obj);
            return null;
        });
    }

    /** Get by the table's primary key. Returns null if the object doesn't exist. */
    public <T> T getById(Class<T> clazz, Serializable id) {
        return execute(session -> session.get(clazz, id));
    }

    /**
     * Executes the query and returns the count. Note that this prepends "select count(*) " to the query automatically.
     */
    public int queryCount(String queryString) {
        // Hibernate returns a long for a count. However, we never expect more than 2 billion rows, for obvious
        // reasons.
        Long count = execute(session -> session.createQuery("select count(*) " + queryString, Long.class)
                .uniqueResult());
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
    public <T> List<T> queryGet(String queryString, Integer offset, Integer limit, Class<T> clazz) {
        return execute(session -> {
            Query<T> query = session.createQuery(queryString, clazz);
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
    public int queryUpdate(String queryString) {
        return execute(session -> session.createQuery(queryString).executeUpdate());
    }

    /** Updates a single object. */
    public void update(Object obj) {
        try {
            execute(session -> {
                session.update(obj);
                return null;
            });
        } catch (OptimisticLockException ex) {
            throw new ConcurrentModificationException("Row has the wrong version number; it may have " + 
                    "been saved in the background.");
        } catch (PersistenceException ex) {
            if (ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                throw new ConstraintViolationException.Builder()
                        .withMessage("Email or phone number conflicts with an existing account.").build();
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
