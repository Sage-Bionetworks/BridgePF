package org.sagebionetworks.bridge.play.http;

import javax.inject.Inject;

import play.api.mvc.EssentialFilter;
import play.filters.cors.CORSFilter;
import play.filters.gzip.GzipFilter;
import play.http.HttpFilters;

public class BridgeFilters implements HttpFilters {

    @Inject
    private CORSFilter corsFilter;

    @Inject
    private GzipFilter gzipFilter;

    public EssentialFilter[] filters() {
        return new EssentialFilter[] { corsFilter, gzipFilter };
    }
}
