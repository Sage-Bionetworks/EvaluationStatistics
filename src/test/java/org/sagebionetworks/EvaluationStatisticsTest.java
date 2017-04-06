package org.sagebionetworks;

import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiHeader;
import org.sagebionetworks.repo.model.wiki.WikiPage;

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
	
	@Test
	public void testWikiRoundTrip() throws Exception {
		WikiContent wikiContent = new WikiContent();
		wikiContent.setMarkdown("this is some content");
		WikiPage wikiPage = es.createWikiPage(projectId, null, "test", wikiContent);
		V2WikiHeader header = new V2WikiHeader();
		
		wikiContent.setMarkdown("this is some other content");
		es.updateWikiPage(projectId, wikiPage.getId(), wikiContent);
	}
}
