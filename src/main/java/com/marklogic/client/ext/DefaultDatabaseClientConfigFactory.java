package com.marklogic.client.ext;

import com.marklogic.client.DatabaseClient;

import java.util.Properties;

public class DefaultDatabaseClientConfigFactory implements DatabaseClientConfigFactory {

	private Properties properties;

	private String defaultHostProperty = "mlHost";
	private String defaultPortProperty = "mlRestPort";
	private String defaultUsernameProperty = "mlUsername";
	private String defaultPasswordProperty = "mlPassword";

	public DefaultDatabaseClientConfigFactory(Properties properties) {
		this.properties = properties;
	}

	@Override
	public DatabaseClientConfig newDatabaseClientConfig(String propertyPrefix) {
		DatabaseClientConfig config = new DatabaseClientConfig();
		config.setCertFile(getProperty(propertyPrefix, "CertFile"));
		config.setCertPassword(getProperty(propertyPrefix, "CertPassword"));

		String type = getProperty(propertyPrefix, "ConnectionType");
		if (!isEmpty(type)) {
			config.setConnectionType(DatabaseClient.ConnectionType.valueOf(type));
		}

		config.setDatabase(getProperty(propertyPrefix, "Database"));
		config.setExternalName(getProperty(propertyPrefix, "ExternalName"));

		config.setHost(getProperty(propertyPrefix, "Host"));

		config.setPassword(getProperty(propertyPrefix, "Password"));

		String port = getProperty(propertyPrefix, "Port");
		if (!isEmpty(port)) {
			config.setPort(Integer.parseInt(port));
		}

		String contextType = getProperty(propertyPrefix, "SecurityContextType");
		if (!isEmpty(contextType)) {
			config.setSecurityContextType(SecurityContextType.valueOf(contextType));
		}

		config.setUsername(getProperty(propertyPrefix, "Username"));

		applyDefaultProperties(config);

		return config;
	}

	protected void applyDefaultProperties(DatabaseClientConfig config) {
		if (isEmpty(config.getHost())) {
			config.setHost(properties.getProperty(defaultHostProperty));
		}

		if (isEmpty(config.getUsername())) {
			config.setUsername(properties.getProperty(defaultUsernameProperty));
		}

		if (isEmpty(config.getPassword())) {
			config.setPassword(properties.getProperty(defaultPasswordProperty));
		}

		if (config.getPort() == 0) {
			String port = properties.getProperty(defaultPortProperty);
			if (!isEmpty(port)) {
				config.setPort(Integer.parseInt(port));
			}
		}
	}

	protected boolean isEmpty(String s) {
		return s == null || s.trim().length() == 0;
	}

	protected String getProperty(String propertyPrefix, String name) {
		return properties.getProperty(propertyPrefix + name);
	}

	public void setDefaultHostProperty(String defaultHostProperty) {
		this.defaultHostProperty = defaultHostProperty;
	}

	public void setDefaultUsernameProperty(String defaultUsernameProperty) {
		this.defaultUsernameProperty = defaultUsernameProperty;
	}

	public void setDefaultPasswordProperty(String defaultPasswordProperty) {
		this.defaultPasswordProperty = defaultPasswordProperty;
	}

	public void setDefaultPortProperty(String defaultPortProperty) {
		this.defaultPortProperty = defaultPortProperty;
	}
}
