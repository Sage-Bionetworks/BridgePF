package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.Lists;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.directory.Directory;

public class DirectoryAccountIteratorTest {

    private static final int TOTAL_NUM_RECORDS = 235;
    
    private StudyIdentifier study = new StudyIdentifierImpl("foo");
    private DirectoryAccountIterator iterator;
    private SortedMap<Integer,Encryptor> encryptors = new TreeMap<>();
    {
        encryptors.put(1, new Encryptor() {
            @Override public Integer getVersion() { return 1; }
            @Override public String encrypt(String text) { return text; }
            @Override public String decrypt(String text) { return text; }
        });
    }
    
    @Before
    public void setUp() {
        List<com.stormpath.sdk.account.Account> accounts = Lists.newArrayList();
        for (int i=0; i < TOTAL_NUM_RECORDS; i++) { // 3 pages...
            com.stormpath.sdk.account.Account account = mock(com.stormpath.sdk.account.Account.class);
            when(account.getEmail()).thenReturn("bridge-tester+"+i+"@sagebridge.org");
            accounts.add(account);
        }
        AccountList page1 = mock(AccountList.class);
        when(page1.iterator()).thenReturn(accounts.subList(0, 100).iterator());
        AccountList page2 = mock(AccountList.class);
        when(page2.iterator()).thenReturn(accounts.subList(100, 200).iterator());
        AccountList page3 = mock(AccountList.class);
        when(page3.iterator()).thenReturn(accounts.subList(200, 235).iterator());
        
        Directory directory = mock(Directory.class);
        when(directory.getAccounts(org.mockito.Matchers.any(AccountCriteria.class))).thenReturn(page1, page2, page3);
        
        iterator = new DirectoryAccountIterator(directory, study, encryptors);
    }
    
    @Test
    public void doesntCrashIfThereAreNoRecords() {
        AccountList page1 = mock(AccountList.class);
        when(page1.iterator()).thenReturn(new ArrayList<com.stormpath.sdk.account.Account>().iterator());
        
        Directory directory = mock(Directory.class);
        when(directory.getAccounts(org.mockito.Matchers.any(AccountCriteria.class))).thenReturn(page1);
        
        iterator = new DirectoryAccountIterator(directory, study, encryptors);
        
        assertFalse(iterator.hasNext());
        try {
            iterator.next();
            fail("Should have thrown an exception");
        } catch(NoSuchElementException e) {
            
        }
    }
    
    @Test
    public void getsAllAccountsInDirectory() {
        int count = 0;
        while (iterator.hasNext() && count < 240) {
            iterator.next();
            count++;
        }
        assertEquals(235, count);
    }
    
}
