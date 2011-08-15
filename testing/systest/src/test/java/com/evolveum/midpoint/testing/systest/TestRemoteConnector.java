/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2011 [name of copyright owner]"
 * 
 */
package com.evolveum.midpoint.testing.systest;

import static org.junit.Assert.*;
import static com.evolveum.midpoint.test.IntegrationTestTools.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.evolveum.midpoint.common.object.ObjectTypeUtil;
import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.AbstractIntegrationTest;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ConnectorHostType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;


/**
 * @author Radovan Semancik
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:application-context-model.xml",
		"classpath:application-context-provisioning.xml",
		"classpath:application-context-systest.xml",
		"classpath:application-context-task.xml" ,
		"classpath:application-context-repository-test.xml"})
public class TestRemoteConnector extends AbstractIntegrationTest {
	
	private static final String CONNECTOR_HOST_LOCALHOST_FILENAME = "src/test/resources/repo/connector-host-localhost.xml";
	private static final String CONNECTOR_HOST_LOCALHOST_OID = "91919191-76e0-59e2-86d6-44cc44cc44cc";
	
	private static final String RESOURCE_FLATFILE_REMOTE_LOCALHOST_FILENAME = "src/test/resources/repo/resource-flatfile-remote.xml";
	private static final String RESOURCE_FLATFILE_REMOTE_LOCALHOST_OID = "ef2bc95b-76e0-59e2-86d6-aaeeffeeffaa";

	@Autowired(required = true)
	private ModelService modelService;

	/**
	 * @throws JAXBException
	 */
	public TestRemoteConnector() throws JAXBException {
		super();
		// TODO Auto-generated constructor stub
	}
	
	// This will get called from the superclass to init the repository
	// It will be called only once
	public void initSystem() throws Exception {
		OperationResult result = new OperationResult("initSystem");
//		addObjectFromFile(SYSTEM_CONFIGURATION_FILENAME);
		
		// This should discover local connectors
		modelService.postInit(result);
			
		addObjectFromFile(CONNECTOR_HOST_LOCALHOST_FILENAME);
			
		// Need to import instead of add, so the (dynamic) connector reference will be resolved
		// correctly
//			importObjectFromFile(RESOURCE_OPENDJ_FILENAME,result);
//			

//			addObjectFromFile(SAMPLE_CONFIGURATION_OBJECT_FILENAME);

	}
	
	/**
	 * Test integrity of the test setup.
	 * 
	 * @throws SchemaException
	 * @throws ObjectNotFoundException
	 */
	@Test
	public void test000Integrity() throws ObjectNotFoundException, SchemaException {
		displayTestTile("test000Integrity");
		assertNotNull(modelService);
		assertNotNull(repositoryService);
		assertTrue(systemInitialized);
		assertNotNull(taskManager);

//		OperationResult result = new OperationResult(TestSanity.class.getName() + ".test000Integrity");
//		ObjectType object = repositoryService.getObject(RESOURCE_OPENDJ_OID, null, result);
//		assertTrue(object instanceof ResourceType);
//		assertEquals(RESOURCE_OPENDJ_OID, object.getOid());
		
		// TODO: test connection to the connector server

	}
	
	/**
	 * Use the connector host definition to trigger discovery of remote connectors.
	 * @throws ObjectNotFoundException 
	 */
	@Test
	public void test001Discovery() throws ObjectNotFoundException {
		displayTestTile("test001Discovery");
		
		// GIVEN
		
		OperationResult result = new OperationResult(TestRemoteConnector.class.getName()+".test001Discovery");
		ConnectorHostType connectorHost = modelService.getObject(CONNECTOR_HOST_LOCALHOST_OID, null, ConnectorHostType.class, result);
		assertNotNull(connectorHost);
		
		// WHEN
		
		Set<ConnectorType> discoveredConnectors = modelService.discoverConnectors(connectorHost, result);
		
		// Then
		
		display("Discovered connectors",discoveredConnectors);
		
		assertFalse("Nothing dicovered",discoveredConnectors.isEmpty());
		for(ConnectorType conn : discoveredConnectors) {
			assertNotNull("No schema for "+ObjectTypeUtil.toShortString(conn),conn.getSchema());
		}
	}
	 
	@Test
	public void test002ImportResource() throws FileNotFoundException {
		displayTestTile("test002ImportResource");
		
		// GIVEN
		
		OperationResult result = new OperationResult(TestRemoteConnector.class.getName()+".test002ImportResource");
		
		// WHEN
		
		importObjectFromFile(RESOURCE_FLATFILE_REMOTE_LOCALHOST_FILENAME,result);
		
		// THEN
		
		// TODO
		
	}
	
	@Test
	public void test003TestConnection() {
		
	}
	
	/**
	 * @param resourceOpendjFilename
	 * @return
	 * @throws FileNotFoundException 
	 */
	private void importObjectFromFile(String filename,OperationResult result) throws FileNotFoundException {
		Task task = taskManager.createTaskInstance();
		FileInputStream stream = new FileInputStream(filename);
		modelService.importObjectsFromStream(stream, task, false, result);
	}
	
}
