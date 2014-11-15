package org.sagebionetworks.bridge.heroku;

import java.io.IOException;
import java.util.EnumSet;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.DomainDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class HerokuDomainDao implements DomainDao {

    private BridgeConfig config;
    
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    
    @Override
    public void addDomain(String identifier) {
        for (Environment env : EnumSet.of(Environment.DEV, Environment.UAT, Environment.PROD)) {
            String studyHostname = getStudyHostname(identifier, env);
            String herokuAppName = getHerokuAppName(env);
            String url = String.format("https://api.heroku.com/apps/%s/domains", herokuAppName);
            
            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod(url);
            post.setRequestHeader("Authorization", "Bearer " + config.getHerokuAuthToken());
            post.setRequestHeader("Accept", "application/vnd.heroku+json; version=3");
            post.setRequestHeader("Content-Type", "application/json");
            try {
                RequestEntity entity = new StringRequestEntity("{\"hostname\":\""+studyHostname+"\"}", "application/json", "UTF-8");
                post.setRequestEntity(entity);
                int result = client.executeMethod(post);
                if (result != 201) {
                    String msg = String.format("%s: could not register domain name %s with Heroku app %s",
                            Integer.toString(result), studyHostname, herokuAppName);
                    throw new BridgeServiceException(msg);
                }
            } catch(IOException e) {
                throw new BridgeServiceException(e);
            }
        }
    }

    @Override
    public void removeDomain(String identifier) {
        for (Environment env : EnumSet.of(Environment.DEV, Environment.UAT, Environment.PROD)) {
            String studyHostname = getStudyHostname(identifier, env);
            String herokuAppName = getHerokuAppName(env);
            String url = String.format("https://api.heroku.com/apps/%s/domains/%s", herokuAppName, studyHostname);

            HttpClient client = new HttpClient();
            DeleteMethod delete = new DeleteMethod(url);
            delete.setRequestHeader("Authorization", "Bearer " + config.getHerokuAuthToken());
            delete.setRequestHeader("Accept", "application/vnd.heroku+json; version=3");
            try {
                int result = client.executeMethod(delete);
                if (result != 200) {
                    String msg = String.format("%s: could not register domain name %s with Heroku app %s",
                            Integer.toString(result), studyHostname, herokuAppName);
                    throw new BridgeServiceException(msg);
                }
            } catch(IOException e) {
                throw new BridgeServiceException(e);
            }
        }
    }
    
    private String getStudyHostname(String identifier, Environment env) {
        return identifier + config.getProperty("study.hostname."+env.name().toLowerCase());
    }
    
    private String getHerokuAppName(Environment env) {
        return config.getProperty("heroku.appname."+env.name().toLowerCase());
    }
}
