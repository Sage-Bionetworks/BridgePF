package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Account;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("unchecked")
public class HibernateHelperTest {
    private static final String QUERY = "from DummyTable";
    private static final Map<String, Object> PARAMETERS = new ImmutableMap.Builder<String, Object>().put("id", 10L)
            .put("studyId", "study-test").build();
    private static final RuntimeException TEST_EXCEPTION = new RuntimeException();

    private HibernateHelper helper;
    @Mock
    private Session mockSession;
    @Mock
    private SessionFactory mockSessionFactory;
    @Mock
    private PersistenceExceptionConverter mockExceptionConverter;
    @Mock
    private Consumer<Account> mockConsumer;
    @Mock
    private Transaction mockTransaction;
    
    @Before
    public void setup() {
        // Spy Hibernate helper. This allows us to mock execute() and test it independently later.
        helper = spy(new HibernateHelper(mockSessionFactory, mockExceptionConverter));
        doAnswer(invocation -> {
            Function<Session, ?> function = invocation.getArgument(0);
            return function.apply(mockSession);
        }).when(helper).execute(any());
    }

    @Test
    public void createSuccess() {
        Object testObj = new Object();
        helper.create(testObj, null);
        verify(mockSession).save(testObj);
    }
    
    @Test
    public void createCallsConsumer() { 
        reset(helper); // clear @Before setup
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);
        
        Account testObj = Account.create();
        
        helper.create(testObj, mockConsumer);
        
        InOrder inOrder = Mockito.inOrder(mockConsumer, mockSession, mockTransaction);
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockSession).save(testObj);
        inOrder.verify(mockConsumer).accept(testObj);
        inOrder.verify(mockTransaction).commit();
    }
    
    @Test
    public void createConsumerTriggersRollback() { 
        reset(helper); // clear @Before setup
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);
        Account testObj = Account.create();
        doThrow(new ProvisionedThroughputExceededException("message")).when(mockConsumer).accept(testObj);
        
        try {
            helper.create(testObj, mockConsumer);
            fail("Should have thrown exception");
        } catch(ProvisionedThroughputExceededException e) {
            InOrder inOrder = Mockito.inOrder(mockConsumer, mockSession, mockTransaction);
            inOrder.verify(mockSession).beginTransaction();
            inOrder.verify(mockSession).save(testObj);
            inOrder.verify(mockConsumer).accept(testObj);
            inOrder.verify(mockSession).close();
            
            verify(mockTransaction, never()).commit();
        }
    }

    @Test
    public void createOtherException() {
        PersistenceException ex = new PersistenceException();
        when(mockSession.save(any())).thenThrow(ex);
        Object testObj = new Object();
        
        when(mockExceptionConverter.convert(ex, testObj)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.create(testObj, null);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, testObj);
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

        when(mockSession.createQuery(QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY, null);
        assertEquals(42, count);
    }

    @Test
    public void queryCountNull() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(null);

        when(mockSession.createQuery(QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY, null);
        assertEquals(0, count);
    }
    
    @Test
    public void queryCountWithParameters() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(42L);

        when(mockSession.createQuery(QUERY, Long.class)).thenReturn(mockQuery);

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
    public void query() {
        Query<Object> mockQuery = mock(Query.class);
        when(mockSession.createQuery(QUERY)).thenReturn(mockQuery);
        
        helper.query(QUERY, PARAMETERS);
        
        verify(mockSession).createQuery(QUERY);
        verify(mockQuery).setParameter("id", 10L);
        verify(mockQuery).setParameter("studyId", "study-test");
        verify(mockQuery).executeUpdate();
    }

    @Test
    public void update() {
        Object testObj = new Object();
        Object received = helper.update(testObj, null);
        assertSame(testObj, received);
        verify(mockSession).update(testObj);
    }

    @Test
    public void updateCallsConsumer() { 
        reset(helper); // clear @Before setup
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);
        
        Account testObj = Account.create();
        
        helper.update(testObj, mockConsumer);
        
        InOrder inOrder = Mockito.inOrder(mockConsumer, mockSession, mockTransaction);
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockSession).update(testObj);
        inOrder.verify(mockConsumer).accept(testObj);
        inOrder.verify(mockTransaction).commit();
        inOrder.verify(mockSession).close();
    }
    
    @Test
    public void updateConsumerTriggersRollback() { 
        reset(helper); // clear @Before setup
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);
        Account testObj = Account.create();
        doThrow(new ProvisionedThroughputExceededException("message")).when(mockConsumer).accept(testObj);
        
        try {
            helper.update(testObj, mockConsumer);
            fail("Should have thrown exception");
        } catch(ProvisionedThroughputExceededException e) {
            InOrder inOrder = Mockito.inOrder(mockConsumer, mockSession, mockTransaction);
            inOrder.verify(mockSession).beginTransaction();
            inOrder.verify(mockSession).update(testObj);
            inOrder.verify(mockConsumer).accept(testObj);
            inOrder.verify(mockSession).close();
            
            verify(mockTransaction, never()).commit();
        }
    }
    
    @Test
    public void execute() {
        // mock session to produce transaction
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);

        // mock session factory
        SessionFactory mockSessionFactory = mock(SessionFactory.class);
        when(mockSessionFactory.openSession()).thenReturn(mockSession);

        helper = new HibernateHelper(mockSessionFactory, mockExceptionConverter);

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
    
    @Test
    public void createConvertsExceptions() throws Exception {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(mockExceptionConverter.convert(ex, account)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.create(account, null);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, account);
    }

    @Test
    public void deleteByIdConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.deleteById(HibernateAccount.class, "whatever");
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void getByIdConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.getById(HibernateAccount.class, "whatever");
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void queryCountConvertsExceptions() { 
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.queryCount("query string", ImmutableMap.of());
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void queryGetConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.queryGet("query string", ImmutableMap.of(), 0, 20, HibernateAccount.class);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void queryUpdateConvertsExceptions() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry 'studyTest-email@email.com' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.queryUpdate("query string", ImmutableMap.of());
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void updateConvertsExceptions() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry 'studyTest-email@email.com' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        
        
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        when(mockExceptionConverter.convert(ex, account)).thenReturn(TEST_EXCEPTION);

        try {
            helper.update(account, null);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(TEST_EXCEPTION, e);
        }
        verify(mockExceptionConverter).convert(ex, account);
    }
    
    @Test
    public void returnsUnconvertedExceptionAsServiceException() throws Exception {
        reset(helper); // clear the doAnswer set up in @before
        
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        PersistenceException pe = new PersistenceException(TEST_EXCEPTION);
        doThrow(pe).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        // This does not convert the exception, it hands it back.
        when(mockExceptionConverter.convert(pe, account)).thenReturn(pe);
        
        try {
            helper.update(account, null);
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            // So we wrap it with a BridgeServiceException
            assertSame(pe, e.getCause());
        }
    }
 
}
