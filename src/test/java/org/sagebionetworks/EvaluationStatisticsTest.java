package org.sagebionetworks;

import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.repo.model.Project;

public class EvaluationStatisticsTest {
	private String projectId;
	private SynapseClient synapseClient;
	private EvaluationStatistics es;
	
	@Before
	public void setUp() throws Exception {
		es = new EvaluationStatistics();
		synapseClient = es.getSynapseClient();
		Project project = new Project();
		project = synapseClient.createEntity(project);
		assertNotNull(project.getId());
		projectId = project.getId();
	}
	
	@After
	public void tearDown() throws Exception {
		synapseClient.deleteEntityById(projectId);
	}
}
