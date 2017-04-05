package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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
    public void create() {
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

    @Test
    public void deleteByIdAllVersions() {
        // mock query
        Query mockQuery = mock(Query.class);
        when(mockSession.createQuery("delete from HibernateSharedModuleMetadata where id='" + MODULE_ID + "'"))
                .thenReturn(mockQuery);

        // execute
        dao.deleteMetadataByIdAllVersions(MODULE_ID);

        // verify backends
        verify(mockQuery).executeUpdate();
        verifySessionAndTransaction();
    }

    @Test
    public void deleteByIdAndVersion() {
        // mock query
        Query mockQuery = mock(Query.class);
        when(mockSession.createQuery("delete from HibernateSharedModuleMetadata where id='" + MODULE_ID +
                "' and version=" + MODULE_VERSION)).thenReturn(mockQuery);

        // execute
        dao.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);

        // validate backends
        verify(mockQuery).executeUpdate();
        verifySessionAndTransaction();
    }

    @Test
    public void allMetadataAllVersions() {
        // mock query
        Query<SharedModuleMetadata> mockQuery = mock(Query.class);
        when(mockSession.createQuery("from HibernateSharedModuleMetadata", SharedModuleMetadata.class)).thenReturn(
                mockQuery);

        List<SharedModuleMetadata> hibernateOutputMetadataList = ImmutableList.of(SharedModuleMetadata.create());
        when(mockQuery.list()).thenReturn(hibernateOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> daoOutputMetadataList = dao.getAllMetadataAllVersions();
        assertSame(hibernateOutputMetadataList, daoOutputMetadataList);

        // validate backends
        verifySessionAndTransaction();
    }

    @Test
    public void byIdAllVersions() {
        // mock query
        Query<SharedModuleMetadata> mockQuery = mock(Query.class);
        when(mockSession.createQuery("from HibernateSharedModuleMetadata where id='" + MODULE_ID + "'",
                SharedModuleMetadata.class)).thenReturn(mockQuery);

        List<SharedModuleMetadata> hibernateOutputMetadataList = ImmutableList.of(SharedModuleMetadata.create());
        when(mockQuery.list()).thenReturn(hibernateOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> daoOutputMetadataList = dao.getMetadataByIdAllVersions(MODULE_ID);
        assertSame(hibernateOutputMetadataList, daoOutputMetadataList);

        // validate backends
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
    public void byIdLatestVersion() {
        // mock query
        Query<SharedModuleMetadata> mockQuery = mock(Query.class);
        when(mockSession.createQuery("from HibernateSharedModuleMetadata where id='" + MODULE_ID +
                "' order by version desc", SharedModuleMetadata.class)).thenReturn(mockQuery);

        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);

        SharedModuleMetadata hibernateOutputMetadata = SharedModuleMetadata.create();
        when(mockQuery.list()).thenReturn(ImmutableList.of(hibernateOutputMetadata));

        // execute and validate
        SharedModuleMetadata daoOutputMetadata = dao.getMetadataByIdLatestVersion(MODULE_ID);
        assertSame(hibernateOutputMetadata, daoOutputMetadata);

        // validate backends
        verify(mockQuery).setMaxResults(1);
        verifySessionAndTransaction();
    }

    @Test
    public void byIdLatestVersionNoResult() {
        // mock query
        Query<SharedModuleMetadata> mockQuery = mock(Query.class);
        when(mockSession.createQuery("from HibernateSharedModuleMetadata where id='" + MODULE_ID +
                "' order by version desc", SharedModuleMetadata.class)).thenReturn(mockQuery);

        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);

        when(mockQuery.list()).thenReturn(ImmutableList.of());

        // execute and validate
        SharedModuleMetadata daoOutputMetadata = dao.getMetadataByIdLatestVersion(MODULE_ID);
        assertNull(daoOutputMetadata);

        // validate backends
        verify(mockQuery).setMaxResults(1);
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
        List<SharedModuleMetadata> daoOutputMetadataList = dao.queryMetadata("foo='bar'");
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
        List<SharedModuleMetadata> daoOutputMetadataList = dao.queryMetadata(null);
        assertSame(hibernateOutputMetadataList, daoOutputMetadataList);

        // validate backends
        verifySessionAndTransaction();
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

    private void verifySessionAndTransaction() {
        verify(mockTransaction).commit();
        verify(mockSession).close();
    }
}
