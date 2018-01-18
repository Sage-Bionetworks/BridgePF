package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import com.google.common.collect.ImmutableList;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

@SuppressWarnings("unchecked")
public class HibernateHelperTest {
    private static final String QUERY = "from DummyTable";

    private HibernateHelper helper;
    private Session mockSession;

    @Before
    public void setup() {
        // mock session
        mockSession = mock(Session.class);

        // Spy Hibernate helper. This allows us to mock execute() and test it independently later.
        helper = spy(new HibernateHelper());
        doAnswer(invocation -> {
            Function<Session, ?> function = invocation.getArgumentAt(0, Function.class);
            return function.apply(mockSession);
        }).when(helper).execute(any());
    }

    @Test
    public void createSuccess() {
        Object testObj = new Object();
        helper.create(testObj);
        verify(mockSession).save(testObj);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void createConcurrentModificationException() {
        // mock session to throw - Need to mock the ConstraintViolationException, because the exception itself is
        // pretty heavy-weight.
        PersistenceException ex = new PersistenceException(mock(org.hibernate.exception.ConstraintViolationException.class));
        when(mockSession.save(any())).thenThrow(ex);

        // setup and execute
        Object testObj = new Object();
        helper.create(testObj);
    }

    @Test(expected = PersistenceException.class)
    public void createOtherException() {
        when(mockSession.save(any())).thenThrow(new PersistenceException());
        Object testObj = new Object();
        helper.create(testObj);
    }

    @Test
    public void delete() {
        // set up
        Object hibernateOutput = new Object();
        when(mockSession.get(Object.class, "test-id")).thenReturn(hibernateOutput);

        // execute and validate
        helper.deleteById(Object.class, "test-id");
        verify(mockSession).delete(hibernateOutput);
    }

    @Test
    public void getById() {
        // set up
        Object hibernateOutput = new Object();
        when(mockSession.get(Object.class, "test-id")).thenReturn(hibernateOutput);

        // execute and validate
        Object helperOutput = helper.getById(Object.class, "test-id");
        assertSame(hibernateOutput, helperOutput);
    }

    @Test
    public void queryCountSuccess() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(42L);

        when(mockSession.createQuery("select count(*) " + QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY);
        assertEquals(42, count);
    }

    @Test
    public void queryCountNull() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(null);

        when(mockSession.createQuery("select count(*) " + QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY);
        assertEquals(0, count);
    }

    @Test
    public void queryGetSuccess() {
        // mock query
        List<Object> hibernateOutputList = ImmutableList.of();
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(hibernateOutputList);

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        List<Object> helperOutputList = helper.queryGet(QUERY, null, null, Object.class);
        assertSame(hibernateOutputList, helperOutputList);
    }

    @Test
    public void queryGetOffsetAndLimit() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(ImmutableList.of());

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and verify we pass through the offset and limit
        helper.queryGet(QUERY, 100, 25, Object.class);
        verify(mockQuery).setFirstResult(100);
        verify(mockQuery).setMaxResults(25);
    }

    @Test
    public void queryUpdate() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.executeUpdate()).thenReturn(7);

        when(mockSession.createQuery(QUERY)).thenReturn(mockQuery);

        // execute and validate
        int numRows = helper.queryUpdate(QUERY);
        assertEquals(7, numRows);
    }

    @Test
    public void update() {
        Object testObj = new Object();
        helper.update(testObj);
        verify(mockSession).update(testObj);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void updateConcurrentModification() {
        // Note: It's the transaction.commit() that throws, not the session.update(). However, to simplify error
        // handling tests, we're going to have the update() throw.
        Object testObj = new Object();
        doThrow(OptimisticLockException.class).when(mockSession).update(testObj);
        helper.update(testObj);
    }
    
    @Test(expected = PersistenceException.class)
    public void updateWithPersistenceException() {
        PersistenceException pe = new PersistenceException();
        
        Object testObj = new Object();
        doThrow(pe).when(mockSession).update(testObj);
        helper.update(testObj);
    }
    
    @Test(expected = org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class)
    public void updateConcurrentModificationException() {
        // mock session to throw - Need to mock the ConstraintViolationException, because the exception itself is
        // pretty heavy-weight.
        PersistenceException ex = new PersistenceException(mock(org.hibernate.exception.ConstraintViolationException.class));
        doThrow(ex).when(mockSession).update(any());

        // setup and execute
        Object testObj = new Object();
        helper.update(testObj);
    }

    @Test
    public void execute() {
        // mock transaction
        Transaction mockTransaction = mock(Transaction.class);

        // mock session to produce transaction
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);

        // mock session factory
        SessionFactory mockSessionFactory = mock(SessionFactory.class);
        when(mockSessionFactory.openSession()).thenReturn(mockSession);

        // un-spy HibernateHelper.execute()
        doCallRealMethod().when(helper).execute(any());
        helper.setHibernateSessionFactory(mockSessionFactory);

        // mock function, so we can verify that it was called, and with the session we expect.
        Object functionOutput = new Object();
        Function<Session, Object> mockFunction = mock(Function.class);
        when(mockFunction.apply(any())).thenReturn(functionOutput);

        // We need to verify mocks in order.
        InOrder inOrder = inOrder(mockSessionFactory, mockSession, mockTransaction, mockFunction);

        // execute and validate
        Object helperOutput = helper.execute(mockFunction);
        assertSame(functionOutput, helperOutput);

        inOrder.verify(mockSessionFactory).openSession();
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockFunction).apply(mockSession);
        inOrder.verify(mockTransaction).commit();
        inOrder.verify(mockSession).close();
    }
}
