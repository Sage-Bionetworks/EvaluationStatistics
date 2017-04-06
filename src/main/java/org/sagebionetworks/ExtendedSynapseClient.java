package org.sagebionetworks;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.reflection.model.PaginatedResults;

public interface ExtendedSynapseClient extends SynapseClient  {

	 PaginatedResults<Evaluation> getReadableEvaluationsPaginated(
			int offset, int limit) throws SynapseException;



}
