package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.persistence.PersistenceException;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.hql.internal.ast.QuerySyntaxException;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.SharedModuleMetadataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

/** Implementation of SharedModuleMetadataDao, using Hibernate backed by a SQL database. */
@Component
public class HibernateSharedModuleMetadataDao implements SharedModuleMetadataDao {
    private SessionFactory hibernateSessionFactory;

    /** Hibernate session factory, used to talk to SQL. Configured via Spring. */
    @Autowired
    public final void setHibernateSessionFactory(SessionFactory hibernateSessionFactory) {
        this.hibernateSessionFactory = hibernateSessionFactory;
    }

    /** {@inheritDoc} */
    @Override
    public SharedModuleMetadata createMetadata(SharedModuleMetadata metadata) {
        try {
            return sessionHelper(session -> {
                session.save(metadata);
                return metadata;
            });
        } catch (PersistenceException ex) {
            // If you try to create a row that already exists, Hibernate will throw a PersistenceException wrapped in a
            // ConstraintViolationException.
            if (ex.getCause() instanceof ConstraintViolationException) {
                throw new ConcurrentModificationException(metadata);
            } else {
                throw ex;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteMetadataByIdAllVersions(String id) {
        sessionHelper(session -> {
            session.createQuery("update HibernateSharedModuleMetadata m set m.deleted = true where m.id='" + id + "'")
                    .executeUpdate();
            return null;
        });
    }

    /** {@inheritDoc} */
    @Override
    public void deleteMetadataByIdAndVersion(String id, int version) {
        sessionHelper(session -> {
            session.createQuery("update HibernateSharedModuleMetadata m set m.deleted = true where m.id='" + id
                    + "' and m.version = " + version).executeUpdate();
            return null;
        });
    }
    
    /** {@inheritDoc} */
    @Override
    public void deleteMetadataByIdAllVersionsPermanently(String id) {
        sessionHelper(session -> {
            session.createQuery("delete from HibernateSharedModuleMetadata where id='" + id + "'").executeUpdate();
            return null;
        });
    }

    /** {@inheritDoc} */
    @Override
    public void deleteMetadataByIdAndVersionPermanently(String id, int version) {
        sessionHelper(session -> {
            session.createQuery("delete from HibernateSharedModuleMetadata where id='" + id + "' and version=" +
                    version).executeUpdate();
            return null;
        });
    }

    /** {@inheritDoc} */
    @Override
    public SharedModuleMetadata getMetadataByIdAndVersion(String id, int version) {
        return sessionHelper(session -> session.get(HibernateSharedModuleMetadata.class,
                new HibernateSharedModuleMetadataKey(id, version)));
    }

    /** {@inheritDoc} */
    @Override
    public List<SharedModuleMetadata> queryMetadata(String whereClause, Map<String, Object> parameters) {
        // build query
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("from HibernateSharedModuleMetadata");
        if (StringUtils.isNotBlank(whereClause)) {
            queryBuilder.append(" where ").append(whereClause);
        }

        // execute query
        try {
            return sessionHelper(session -> {
                Query<SharedModuleMetadata> query = session.createQuery(queryBuilder.toString(), SharedModuleMetadata.class);
                if (parameters != null) {
                    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                        if (entry.getValue() instanceof List) {
                            query.setParameterList(entry.getKey(), (List<?>)entry.getValue());
                        } else {
                            query.setParameter(entry.getKey(), entry.getValue());
                        }
                    }
                }
                return query.list();
            });
        } catch (IllegalArgumentException ex) {
            // Similarly, an invalid query will result in an IllegalArgumentException which wraps a
            // QuerySyntaxException.
            if (ex.getCause() instanceof QuerySyntaxException) {
                throw new BadRequestException(ex);
            } else {
                throw ex;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public SharedModuleMetadata updateMetadata(SharedModuleMetadata metadata) {
        return sessionHelper(session -> {
            session.update(metadata);
            return metadata;
        });
    }

    // Helper function, which handles opening and closing sessions and transactions.
    private <T> T sessionHelper(Function<Session, T> function) {
        T retval;
        try (Session session = hibernateSessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            retval = function.apply(session);
            transaction.commit();
        }
        return retval;
    }
}
