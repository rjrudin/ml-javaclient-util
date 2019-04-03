package com.marklogic.client.ext;

import com.marklogic.client.DatabaseClient;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class DefaultDatabaseClientConfigFactoryTest extends Assert {

	private Properties props = new Properties();

	@Test
	public void allPropertiesSet() {
		props.setProperty("mlTestCertFile", "/some/file");
		props.setProperty("mlTestCertPassword", "certword");
		props.setProperty("mlTestConnectionType", DatabaseClient.ConnectionType.GATEWAY.name());
		props.setProperty("mlTestDatabase", "somedb");
		props.setProperty("mlTestExternalName", "somename");
		props.setProperty("mlTestHost", "testhost");
		props.setProperty("mlTestPassword", "testword");
		props.setProperty("mlTestPort", "8123");
		props.setProperty("mlTestSecurityContextType", SecurityContextType.CERTIFICATE.name());
		props.setProperty("mlTestUsername", "testuser");

		// These should not be used
		props.setProperty("mlHost", "mlhost");
		props.setProperty("mlUsername", "mlusername");
		props.setProperty("mlPassword", "mlpassword");
		props.setProperty("mlRestPort", "8003");

		DatabaseClientConfig config = new DefaultDatabaseClientConfigFactory(props).newDatabaseClientConfig("mlTest");
		assertEquals("/some/file", config.getCertFile());
	}
}
