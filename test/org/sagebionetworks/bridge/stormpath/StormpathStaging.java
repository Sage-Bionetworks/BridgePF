package org.sagebionetworks.bridge.stormpath;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.crypto.Encryptor;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Iterators;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.AccountStoreMapping;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.ClientBuilder;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.impl.client.DefaultClientBuilder;

public class StormpathStaging {

    @Before
    public void before() {
        // https://enterprise.stormpath.io/v1/applications/48EYxi6NASzM7Di7J5ORU3

        Study study = new DynamoStudy();
        study.setIdentifier("test");
        
        SortedMap<Integer,Encryptor> encryptors = new TreeMap<>();
        
        ApiKey apiKey = ApiKeys.builder()
            .setId("6WDWHIPN98947VL1T835A9HAK")
            .setSecret("ladZVFRkm+iGQmnl49YkB+pAD6Kmiugl/kkk7gPe1xU").build();
        
        ClientBuilder clientBuilder = Clients.builder().setApiKey(apiKey);
        ((DefaultClientBuilder)clientBuilder).setBaseUrl("https://enterprise.stormpath.io/v1");
        
        Client client = clientBuilder.build();

        Iterator<org.sagebionetworks.bridge.models.accounts.Account> combinedIterator = null;
        
        Application app = client.getResource("https://enterprise.stormpath.io/v1/applications/48EYxi6NASzM7Di7J5ORU3", Application.class);
        for (AccountStoreMapping mapping : app.getAccountStoreMappings()) {
            Directory directory = client.getResource(mapping.getAccountStore().getHref(), Directory.class);
            System.out.println(directory.getName() + ": " + directory.getDescription());
            if (combinedIterator ==  null) {
                combinedIterator = new StormpathAccountIterator(study, encryptors, directory.getAccounts().iterator());
            } else {
                combinedIterator = Iterators.concat(combinedIterator, new StormpathAccountIterator(study, encryptors, directory.getAccounts().iterator()));
            }
        }
        
        while (combinedIterator.hasNext()) {
            System.out.println("    " + combinedIterator.next());
        }
    }
    
    @Test
    public void test() {
        
    }
    
}
