package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;

/**
 * This demonstrates the iterator works, but that's not the interesting thing here: the interesting thing is:
 * does Stormpath retrieve additional pages? You need enough accounts to verify and we only have that in 
 * production.
 */
public class StormpathAccountIteratorTest {
    
    @Test
    public void canIterateThroughMultipleStudiesMultiplePages() {
        StormpathAccountDao accountDao = getMockAccountDao();
        
        Iterator<Account> accounts = accountDao.getAllAccounts();
        assertEquals(370, count(accounts));
    }
    
    @Test
    public void canIterateThroughOneStudyMultiplePages() {
        StormpathAccountDao accountDao = getMockAccountDao();
        
        Iterator<Account> accounts = accountDao.getStudyAccounts(createStudy("study1"));
        assertEquals(10, count(accounts));
    }

    @Test
    public void getAllAccountsIteratorConcatenation() {
        Iterator<String> combined = null;
        for (int i=0; i < 3; i++) {
            List<String> list = Lists.newArrayList("a","b");
            if (combined == null) {
                combined = list.iterator();
            } else {
                combined = Iterators.concat(combined, list.iterator());
            }
        }
        List<String> allLetters = Lists.newArrayList();
        while(combined.hasNext()) {
            allLetters.add(combined.next());
        }
        assertEquals(Lists.newArrayList("a","b","a","b","a","b"), allLetters);
    }
    
    @SuppressWarnings("unchecked")
    private StormpathAccountDao getMockAccountDao() {
        StormpathAccountDao accountDao = new StormpathAccountDao();
        
        Study study1 = createStudy("study1");
        Study study2 = createStudy("study2");
        Study study3 = createStudy("study3");
        Directory dir1 = createDirectoryMock(study1, 10);
        Directory dir2 = createDirectoryMock(study2, 250);
        Directory dir3 = createDirectoryMock(study3, 110);
        
        Client client = mock(Client.class);
        when(client.getResource(any(String.class),(Class<Directory>)any(Class.class))).thenReturn(dir1, dir2, dir3);

        List<Study> studyList = Lists.newArrayList(study1, study2, study3);
        
        StudyService studyService = mock(StudyService.class);
        when(studyService.getStudies()).thenReturn(studyList);
        
        accountDao.setStormpathClient(client);
        accountDao.setStudyService(studyService);
        return accountDao;
    }
    
    private Study createStudy(String href) {
        Study study = TestUtils.getValidStudy(StormpathAccountIteratorTest.class);
        study.setStormpathHref(href);
        return study;
    }
    
    private Directory createDirectoryMock(Study study, int numAccounts) {
        List<com.stormpath.sdk.account.Account> accounts = Lists.newArrayList();
        for (int i=0; i < numAccounts; i++) {
            com.stormpath.sdk.account.Account acct = mock(com.stormpath.sdk.account.Account.class);
            accounts.add(acct);
        }
        AccountList list = mock(AccountList.class);
        when(list.iterator()).thenReturn(accounts.iterator());
        
        Directory dir = mock(Directory.class);
        when(dir.getHref()).thenReturn(study.getStormpathHref());
        when(dir.getAccounts(any(AccountCriteria.class))).thenReturn(list);
        return dir;
    }
    
    private int count(Iterator<Account> accounts) {
        int count = 0;
        while(accounts.hasNext()) {
            accounts.next();
            count++;
        }
        return count;
    }
    
}
