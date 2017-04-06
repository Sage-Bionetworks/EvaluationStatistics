package org.sagebionetworks;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.reflection.model.PaginatedResults;
import org.sagebionetworks.simpleHttpClient.SimpleHttpClientConfig;

public class ExtendedSynapseClientImpl extends SynapseClientImpl implements ExtendedSynapseClient {

	public ExtendedSynapseClientImpl() {
		// TODO Auto-generated constructor stub
	}

	public ExtendedSynapseClientImpl(SimpleHttpClientConfig config) {
		super(config);
		// TODO Auto-generated constructor stub
	}
	
	public PaginatedResults<Evaluation> getReadableEvaluationsPaginated(
			int offset, int limit) throws SynapseException {
		String url = "/evaluation?" + OFFSET + "="
				+ offset + "&limit=" + limit;
		return getPaginatedResults(getRepoEndpoint(), url, Evaluation.class);
	}



}
