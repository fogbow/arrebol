package org.fogbowcloud.app.jdfcompiler;

public class FakeConfiguration extends Configuration {

	private static final long serialVersionUID = 1L;

	public static final String FAKE = FakeConfiguration.class.getName();

	@Override
	public String getConfDir() {

		return null;
	}
}