package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;

import com.google.common.collect.ImmutableList;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.hql.internal.ast.QuerySyntaxException;
import org.hibernate.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class HibernateSharedModuleMetadataDaoTest {
    private static final String MODULE_ID = "test-module";
    private static final int MODULE_VERSION = 3;

    private HibernateSharedModuleMetadataDao dao;
    private Session mockSession;
    private Transaction mockTransaction;

    @Before
    public void before() {
        // mock transaction
        mockTransaction = mock(Transaction.class);

        // mock session
        mockSession = mock(Session.class);
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);

        // mock session factory
        SessionFactory mockSessionFactory = mock(SessionFactory.class);
        when(mockSessionFactory.openSession()).thenReturn(mockSession);

        // setup dao
        dao = new HibernateSharedModuleMetadataDao();
        dao.setHibernateSessionFactory(mockSessionFactory);
    }

    @Test
    public void createSuccess() {
        // setup input and execute
        SharedModuleMetadata daoInputMetadata = SharedModuleMetadata.create();
        SharedModuleMetadata daoOutputMetadata = dao.createMetadata(daoInputMetadata);

        // validate backends
        ArgumentCaptor<SharedModuleMetadata> hibernateInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        verify(mockSession).save(hibernateInputMetadataCaptor.capture());
        verifySessionAndTransaction();

        // validate data flow
        SharedModuleMetadata hibernateInputMetadata = hibernateInputMetadataCaptor.getValue();
        assertSame(daoInputMetadata, hibernateInputMetadata);
        assertSame(hibernateInputMetadata, daoOutputMetadata);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void createConstraintViolation() {
        // mock dao to throw - Need to mock the ConstraintViolationException, because the exception itself is pretty
        // heavy-weight.
        PersistenceException ex = new PersistenceException(mock(ConstraintViolationException.class));
        when(mockSession.save(any())).thenThrow(ex);

        // setup input and execute
        dao.createMetadata(SharedModuleMetadata.create());
    }

    @Test(expected = PersistenceException.class)
    public void createOtherException() {
        when(mockSession.save(any())).thenThrow(new PersistenceException());
        dao.createMetadata(SharedModuleMetadata.create());
    }

    @Test
    public void deleteByIdAllVersions() {
        Query mockQuery = mock(Query.class);
        when(mockSession.createQuery("update HibernateSharedModuleMetadata m set m.deleted = true where m.id='" + MODULE_ID + "'"))
                .thenReturn(mockQuery);
        
        dao.deleteMetadataByIdAllVersions(MODULE_ID);
        
        verify(mockSession).createQuery("update HibernateSharedModuleMetadata m set m.deleted = true where m.id='" + MODULE_ID + "'");
        verifySessionAndTransaction();
    }

    @Test
    public void deleteByIdAndVersion() {
        Query mockQuery = mock(Query.class);
        when(mockSession.createQuery("update HibernateSharedModuleMetadata m set m.deleted = true where m.id='"
                + MODULE_ID + "' and m.version = " + MODULE_VERSION)).thenReturn(mockQuery);

        dao.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);

        verify(mockSession).createQuery("update HibernateSharedModuleMetadata m set m.deleted = true where m.id='"
                + MODULE_ID + "' and m.version = " + MODULE_VERSION);
        verifySessionAndTransaction();
    }
    
    @Test
    public void deleteByIdAllVersionsPermanently() {
        // mock query
        Query mockQuery = mock(Query.class);
        when(mockSession.createQuery("delete from HibernateSharedModuleMetadata where id='" + MODULE_ID + "'"))
                .thenReturn(mockQuery);

        // execute
        dao.deleteMetadataByIdAllVersionsPermanently(MODULE_ID);

        // verify backends
        verify(mockQuery).executeUpdate();
        verifySessionAndTransaction();
    }

    @Test
    public void deleteByIdAndVersionPermanently() {
        // mock query
        Query mockQuery = mock(Query.class);
        when(mockSession.createQuery("delete from HibernateSharedModuleMetadata where id='" + MODULE_ID +
                "' and version=" + MODULE_VERSION)).thenReturn(mockQuery);

        // execute
        dao.deleteMetadataByIdAndVersionPermanently(MODULE_ID, MODULE_VERSION);

        // validate backends
        verify(mockQuery).executeUpdate();
        verifySessionAndTransaction();
    }

    @Test
    public void byIdAndVersion() {
        // mock session with metadata
        HibernateSharedModuleMetadataKey metadataKey = new HibernateSharedModuleMetadataKey(MODULE_ID, MODULE_VERSION);
        HibernateSharedModuleMetadata hibernateOutputMetadata = new HibernateSharedModuleMetadata();
        when(mockSession.get(HibernateSharedModuleMetadata.class, metadataKey)).thenReturn(hibernateOutputMetadata);

        // execute and validate
        SharedModuleMetadata daoOutputMetadata = dao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
        assertSame(hibernateOutputMetadata, daoOutputMetadata);

        // validate backends
        verifySessionAndTransaction();
    }

    @Test
    public void query() {
        // mock query
        Query<SharedModuleMetadata> mockQuery = mock(Query.class);
        when(mockSession.createQuery("from HibernateSharedModuleMetadata where foo='bar'", SharedModuleMetadata.class))
                .thenReturn(mockQuery);

        List<SharedModuleMetadata> hibernateOutputMetadataList = ImmutableList.of(SharedModuleMetadata.create());
        when(mockQuery.list()).thenReturn(hibernateOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> daoOutputMetadataList = dao.queryMetadata("foo='bar'", null);
        assertSame(hibernateOutputMetadataList, daoOutputMetadataList);

        // validate backends
        verifySessionAndTransaction();
    }

    @Test
    public void nullQuery() {
        // mock query
        Query<SharedModuleMetadata> mockQuery = mock(Query.class);
        when(mockSession.createQuery("from HibernateSharedModuleMetadata", SharedModuleMetadata.class))
                .thenReturn(mockQuery);

        List<SharedModuleMetadata> hibernateOutputMetadataList = ImmutableList.of(SharedModuleMetadata.create());
        when(mockQuery.list()).thenReturn(hibernateOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> daoOutputMetadataList = dao.queryMetadata(null, null);
        assertSame(hibernateOutputMetadataList, daoOutputMetadataList);

        // validate backends
        verifySessionAndTransaction();
    }

    @Test(expected = BadRequestException.class)
    public void queryBadQuery() {
        when(mockSession.createQuery("from HibernateSharedModuleMetadata where blargg", SharedModuleMetadata.class))
                .thenThrow(new IllegalArgumentException(new QuerySyntaxException("error message")));
        dao.queryMetadata("blargg", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void queryOtherException() {
        when(mockSession.createQuery("from HibernateSharedModuleMetadata where foo='bar'", SharedModuleMetadata.class))
                .thenThrow(new IllegalArgumentException());
        dao.queryMetadata("foo='bar'", null);
    }

    @Test
    public void update() {
        // setup input and execute
        SharedModuleMetadata daoInputMetadata = SharedModuleMetadata.create();
        SharedModuleMetadata daoOutputMetadata = dao.updateMetadata(daoInputMetadata);

        // validate backends
        ArgumentCaptor<SharedModuleMetadata> hibernateInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        verify(mockSession).update(hibernateInputMetadataCaptor.capture());
        verifySessionAndTransaction();

        // validate data flow
        SharedModuleMetadata hibernateInputMetadata = hibernateInputMetadataCaptor.getValue();
        assertSame(daoInputMetadata, hibernateInputMetadata);
        assertSame(hibernateInputMetadata, daoOutputMetadata);
    }
    
    @Test
    public void parametersAreSetForQuery() {
        // mock query
        Query<SharedModuleMetadata> mockQuery = mock(Query.class);
        when(mockSession.createQuery("from HibernateSharedModuleMetadata where foo='bar'", SharedModuleMetadata.class))
                .thenReturn(mockQuery);

        List<SharedModuleMetadata> hibernateOutputMetadataList = ImmutableList.of(SharedModuleMetadata.create());
        when(mockQuery.list()).thenReturn(hibernateOutputMetadataList);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", "name");
        parameters.put("notes", ImmutableList.of(1,2,3));
        
        // execute and validate
        List<SharedModuleMetadata> daoOutputMetadataList = dao.queryMetadata("foo='bar'", parameters);
        assertSame(hibernateOutputMetadataList, daoOutputMetadataList);

        // validate backends
        verifySessionAndTransaction();
        verify(mockQuery).setParameter("name", "name");
        verify(mockQuery).setParameterList("notes", ImmutableList.of(1,2,3));
    }

    private void verifySessionAndTransaction() {
        verify(mockTransaction).commit();
        verify(mockSession).close();
    }
}
