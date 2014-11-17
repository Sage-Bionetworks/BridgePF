package org.sagebionetworks.bridge.heroku;

import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.HerokuApi;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.node.ArrayNode;

public class HerokuApiImpl implements HerokuApi {

    private BridgeConfig config;
    
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    
    @Override
    public String registerDomainForStudy(String identifier) {
        String studyHostname = getStudyHostname(identifier);
        String herokuAppName = getHerokuAppName();
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
        return getDomainRegistrationForStudy(identifier);
    }

    @Override
    public String getDomainRegistrationForStudy(String identifier) {
        String herokuAppName = getHerokuAppName();
        String url = String.format("https://api.heroku.com/apps/%s/domains", herokuAppName);
        
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(url);
        get.setRequestHeader("Authorization", "Bearer " + config.getHerokuAuthToken());
        get.setRequestHeader("Accept", "application/vnd.heroku+json; version=3");
        try {
            int result = client.executeMethod(get);
            if (result != 200) {
                String msg = String.format("%s: Could not get domains for Heroku app %s", Integer.toString(result),
                        herokuAppName);
                throw new BridgeServiceException(msg);
            }
            String domainString = get.getResponseBodyAsString();
            ArrayNode array = (ArrayNode)BridgeObjectMapper.get().readTree(domainString);
            for (int i=0; i < array.size(); i++) {
                String hostname = array.get(i).get("hostname").asText();
                if (hostname.startsWith(identifier)) {
                    return hostname;
                }
            }
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
        return null;
    }

    @Override
    public void unregisterDomainForStudy(String identifier) {
        String studyHostname = getStudyHostname(identifier);
        String herokuAppName = getHerokuAppName();
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
    
    private String getStudyHostname(String identifier) {
        return identifier + config.getProperty("study.hostname."+config.getEnvironment().name().toLowerCase());
    }
    
    private String getHerokuAppName() {
        return config.getProperty("heroku.appname."+config.getEnvironment().name().toLowerCase());
    }
}
