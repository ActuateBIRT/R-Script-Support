/*******************************************************************************
 * Copyright (c) 2017 Actuate. All Rights Reserved.
 * Trademarks owned by Actuate.
 * "OpenText" is a trademark of Open Text.
 *******************************************************************************/

package com.actuate.birt.script.ext.rserve;

import java.util.Map;
import java.util.logging.Logger;

import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * Factory class to manage connection to Rserve
 */
public class RserveConnectionFactory {
	private static Logger logger = Logger.getLogger( RserveConnectionFactory.class.getName() );
	
	// Connection property keys
	static public String PROP_HOST = "host";
	static public String PROP_PORT = "port";
	static public String PROP_USER = "user";
	static public String PROP_PASSWORD = "password";
	
	public RserveConnectionFactory() {
	}

	/**
	 * Obtain a connection to Rserve. Returned RConnection is enclosed in an AutoCloseable wrapper. Call
	 *    the close method on the returned wrapper object to dispose of the RConnection.
	 * @param connectionProperties Map that contains "host", "port", and, optionally, "user"and "password" properties 
	 * @throws RserveException 
	 */
	public AutoCloseable getConnection( Map<String, Object> connectionProperties ) 
			throws RserveException {
		String host = (String) connectionProperties.get( PROP_HOST );
		if ( host == null || host.isEmpty() ) {
			throw new IllegalArgumentException( PROP_HOST );
		}
		
		int port = 0;
		Object portObj = connectionProperties.get(PROP_PORT);
		if ( portObj != null ) {
			if ( portObj instanceof Number )
				port = ((Number) portObj).intValue();
			else 
				port = Integer.parseInt( portObj.toString() );
		}
		
		String user = (String) connectionProperties.get( PROP_USER);
		String password = (String) connectionProperties.get( PROP_PASSWORD);
		
		logger.info( "getConnection: host=" + host + ", port=" + port + ", user=" + user);
		
		RConnection rconn;
		if ( port > 0 )
			rconn = new RConnection(host, port);
		else 
			rconn = new RConnection(host);
		
		if ( rconn.needLogin() ) {
			// It appears that we must do the login() call, otherwise  communication with
			// Rserve may get messed up and the connection will hang. 
			// So send empty user name/password if none supplied
			if (user == null)
				user = "";
			if (password ==  null)
				password = "";
			rconn.login( user, password );
		}
		
		// Wrap RConnection in AutoCloseable interface
		return new RConnectionWrapper(rconn);
	}
	
	/**
	 * AutoCloseable wrapper of RConnection
	 */
	public static class RConnectionWrapper implements AutoCloseable {
		private RConnection rConnection;

		public RConnectionWrapper(RConnection rconn) {
			rConnection = rconn;
		}

		@Override
		public void close() {
			if ( rConnection != null ) {
				rConnection.close();
				rConnection = null;
			}
		}
		
		public RConnection getRConnection() {
			return rConnection;
		}
	}
	
}
