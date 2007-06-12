package com.webreach.mirth.server.controllers.tests;

import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.webreach.mirth.model.ConnectorMetaData;
import com.webreach.mirth.server.controllers.ConfigurationController;
import com.webreach.mirth.server.controllers.ControllerException;
import com.webreach.mirth.server.tools.ScriptRunner;

public class ConfigurationControllerTest extends TestCase {
	private ConfigurationController configurationController = ConfigurationController.getInstance();

	protected void setUp() throws Exception {
		super.setUp();
		// clear all database tables
		ScriptRunner.runScript("derby-database.sql");

		// initialize the configuration controller to cache encryption key
		configurationController.initialize();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testGetTransports() throws ControllerException {
		ConnectorMetaData sampleTransport = new ConnectorMetaData();
		sampleTransport.setName("FTP Reader");
		sampleTransport.setServerClassName("com.webreach.mirth.server.mule.providers.ftp.FtpConnector");
		sampleTransport.setProtocol("ftp");
		sampleTransport.setTransformers("ByteArrayToString");
		sampleTransport.setType(ConnectorMetaData.Type.LISTENER);
		Map<String, ConnectorMetaData> testTransportList = configurationController.getConnectorMetaData();

		Assert.assertTrue(testTransportList.containsValue(sampleTransport));
	}
}