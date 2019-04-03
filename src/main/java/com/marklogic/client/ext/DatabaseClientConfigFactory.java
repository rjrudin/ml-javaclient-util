package com.marklogic.client.ext;

/**
 * The intent of this interface is to abstract how a DatabaseClientConfig is constructed from a set of properties, where
 * it is common to use a prefix for all the properties associated with a particular connection. For example, for
 * connecting to a test database, it is convenient to prefix all of the properties with "mlTest" - "mlTestUsername",
 * "mlTestPassword", "mlTestExternalName", etc.
 * <p>
 * This does not cover setting an SSLContext, SSLHostnameVerifier, or TrustManager, as those are not typically set via
 * properties.
 */
public interface DatabaseClientConfigFactory {

	DatabaseClientConfig newDatabaseClientConfig(String propertyPrefix);

}
