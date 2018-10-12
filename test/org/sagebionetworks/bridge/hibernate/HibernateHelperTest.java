package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;

@SuppressWarnings("unchecked")
public class HibernateHelperTest {
    private static final String QUERY = "from DummyTable";
    private static final Map<String, Object> PARAMETERS = new ImmutableMap.Builder<String, Object>().put("id", 10L)
            .put("studyId", "study-test").build();

    private HibernateHelper helper;
    private Session mockSession;
    private SessionFactory mockSessionFactory;
    
    @Before
    public void setup() {
        // mock session
        mockSession = mock(Session.class);
        mockSessionFactory = mock(SessionFactory.class);

        // Spy Hibernate helper. This allows us to mock execute() and test it independently later.
        helper = spy(new HibernateHelper(mockSessionFactory, new AccountPersistentExceptionConverter()));
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

    @Test(expected = BridgeServiceException.class)
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
        int count = helper.queryCount(QUERY, null);
        assertEquals(42, count);
    }

    @Test
    public void queryCountNull() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(null);

        when(mockSession.createQuery("select count(*) " + QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY, null);
        assertEquals(0, count);
    }
    
    @Test
    public void queryCountWithParameters() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(42L);

        when(mockSession.createQuery("select count(*) " + QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY, PARAMETERS);
        assertEquals(42, count);
        
        verify(mockQuery).setParameter("studyId", "study-test");
        verify(mockQuery).setParameter("id", 10L);
    }

    @Test
    public void queryGetSuccess() {
        // mock query
        List<Object> hibernateOutputList = ImmutableList.of();
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(hibernateOutputList);

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        List<Object> helperOutputList = helper.queryGet(QUERY, null, null, null, Object.class);
        assertSame(hibernateOutputList, helperOutputList);
    }

    @Test
    public void queryGetOffsetAndLimit() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(ImmutableList.of());

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and verify we pass through the offset and limit
        helper.queryGet(QUERY, null, 100, 25, Object.class);
        verify(mockQuery).setFirstResult(100);
        verify(mockQuery).setMaxResults(25);
    }
    
    @Test
    public void queryGetWithParameters() {
        // mock query
        List<Object> hibernateOutputList = ImmutableList.of();
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(hibernateOutputList);

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        List<Object> helperOutputList = helper.queryGet(QUERY, PARAMETERS, null, null, Object.class);
        assertSame(hibernateOutputList, helperOutputList);
        
        verify(mockQuery).setParameter("studyId", "study-test");
        verify(mockQuery).setParameter("id", 10L);
    }

    @Test
    public void queryUpdate() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.executeUpdate()).thenReturn(7);

        when(mockSession.createQuery(QUERY)).thenReturn(mockQuery);

        // execute and validate
        int numRows = helper.queryUpdate(QUERY, null);
        assertEquals(7, numRows);
    }
    
    @Test
    public void queryUpdateWithParameters() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.executeUpdate()).thenReturn(7);

        when(mockSession.createQuery(QUERY)).thenReturn(mockQuery);

        // execute and validate
        int numRows = helper.queryUpdate(QUERY, PARAMETERS);
        assertEquals(7, numRows);
        
        verify(mockQuery).setParameter("studyId", "study-test");
        verify(mockQuery).setParameter("id", 10L);
    }

    @Test
    public void update() {
        Object testObj = new Object();
        Object received = helper.update(testObj);
        assertSame(testObj, received);
        verify(mockSession).update(testObj);
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
        // doCallRealMethod().when(helper).execute(any());
        //helper.setHibernateSessionFactory(mockSessionFactory);
        helper = new HibernateHelper(mockSessionFactory, new AccountPersistentExceptionConverter());

        // mock function, so we can verify that it was called, and with the session we expect.
        Object functionOutput = new Object();
        Function<Session, Object> mockFunction = mock(Function.class);
        when(mockFunction.apply(any())).thenReturn(functionOutput);

        // We need to verify mocks in order.
        InOrder inOrder = inOrder(mockSessionFactory, mockSession, mockTransaction, mockFunction);

        // execute and validate
        Object helperOutput = helper.executeWithExceptionHandling(null, mockFunction);
        assertSame(functionOutput, helperOutput);

        inOrder.verify(mockSessionFactory).openSession();
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockFunction).apply(mockSession);
        inOrder.verify(mockTransaction).commit();
        inOrder.verify(mockSession).close();
    }
    
    // These methods verify that the helper is using the exception converter. The exact behavior of the
    // converter is tested separately.
    
    @Test(expected = ConcurrentModificationException.class)
    public void createConvertsExceptions() throws Exception {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(new OptimisticLockException()).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        
        helper.create(account);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void deleteByIdConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(new OptimisticLockException()).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        
        helper.deleteById(HibernateAccount.class, "whatever");
    }
    
    @Test(expected = ConcurrentModificationException.class)
    public void getByIdConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(new OptimisticLockException()).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        
        helper.getById(HibernateAccount.class, "whatever");
    }
    
    @Test(expected = ConcurrentModificationException.class)
    public void queryCountConvertsExceptions() { 
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(new OptimisticLockException()).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        
        helper.queryCount("query string", ImmutableMap.of());
    }
    
    @Test(expected = ConcurrentModificationException.class)
    public void queryGetConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(new OptimisticLockException()).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        
        helper.queryGet("query string", ImmutableMap.of(), 0, 20, HibernateAccount.class);
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void queryUpdateConvertsExceptions() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry 'studyTest-email@email.com' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");

        helper.update(account);    
    }
 
}
