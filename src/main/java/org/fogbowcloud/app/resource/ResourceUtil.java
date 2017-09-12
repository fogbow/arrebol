package org.fogbowcloud.app.resource;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.fogbowcloud.app.model.User;
import org.fogbowcloud.app.restlet.JDFSchedulerApplication;
import org.fogbowcloud.app.utils.ArrebolPropertiesConstants;
import org.restlet.util.Series;

class ResourceUtil {

	static User authenticateUser(JDFSchedulerApplication application, Series headers)
            throws IOException, GeneralSecurityException {
        String credentials = headers.getFirstValue(ArrebolPropertiesConstants.X_CREDENTIALS);
        return application.authUser(credentials);
	}
}
