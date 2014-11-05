package org.sagebionetworks.bridge.crypto;

import java.io.IOException;

import org.bouncycastle.cms.CMSException;

public interface CmsSignedDataProcessor {

     String sign(String base64Encoded) throws IOException, CMSException;
}
