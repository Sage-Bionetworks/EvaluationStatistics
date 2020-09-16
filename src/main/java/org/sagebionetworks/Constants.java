package org.sagebionetworks;

import java.util.Arrays;
import java.util.List;

import org.sagebionetworks.client.exceptions.SynapseBadRequestException;
import org.sagebionetworks.client.exceptions.SynapseConflictingUpdateException;
import org.sagebionetworks.client.exceptions.SynapseDeprecatedServiceException;
import org.sagebionetworks.client.exceptions.SynapseForbiddenException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.client.exceptions.SynapseResultNotReadyException;
import org.sagebionetworks.client.exceptions.SynapseServerException;
import org.sagebionetworks.client.exceptions.SynapseTermsOfUseException;
import org.sagebionetworks.client.exceptions.SynapseUnauthorizedException;

public class Constants {

	
	public static final List<Class<? extends SynapseServerException>> NO_RETRY_EXCEPTIONS = Arrays.asList(
			SynapseResultNotReadyException.class,
			SynapseNotFoundException.class,
			SynapseBadRequestException.class,
			SynapseConflictingUpdateException.class,
			SynapseDeprecatedServiceException.class,
			SynapseForbiddenException.class, 
			SynapseTermsOfUseException.class,
			SynapseUnauthorizedException.class
			); 
	
	public static final Integer[] NO_RETRY_STATUSES = new Integer[] {409};
	
	public static int DEFAULT_NUM_RETRY_ATTEMPTS = 8; // 63 sec
	

	

}
