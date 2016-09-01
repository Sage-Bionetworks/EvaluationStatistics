package org.sagebionetworks;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.apache.http.HttpStatus;
import org.sagebionetworks.client.exceptions.SynapseServerException;

import com.amazonaws.services.lambda.model.ServiceException;


public class ExponentialBackoffUtil {
	private static Logger log = Logger.getLogger(ExponentialBackoffUtil.class.getName());


	private static int NUM_ATTEMPTS = 7;
	private static long INITIAL_BACKOFF_MILLIS = 500L;
	private static long BACKOFF_MULTIPLIER = 2L;
	
	private static final List<Integer> NO_RETRY_STATUSES = Arrays.asList(
		HttpStatus.SC_NOT_FOUND, //SynapseNotFoundException
		HttpStatus.SC_BAD_REQUEST, // SynapseBadRequestException
		HttpStatus.SC_PRECONDITION_FAILED, // SynapseConflictingUpdateException
		HttpStatus.SC_GONE, // SynapseDeprecatedServiceException
		HttpStatus.SC_FORBIDDEN, // SynapseForbiddenException, SynapseTermsOfUseException
		HttpStatus.SC_UNAUTHORIZED, // SynapseUnauthorizedException
		HttpStatus.SC_CONFLICT // 409
	);
	
	private static String exceptionMessage(Throwable e) {
		if (e==null) return null;
		if (e instanceof SynapseServerException) {
			return ""+((SynapseServerException)e).getStatusCode()+" "+e.getMessage();
		}
		return e.getMessage();
	}
	
	/**
	 * 
	 * Note, the total sleep time before giving up is:
	 * INITIAL_BACKOFF_MILLIS * (BACKOFF_MULTIPLIER ^ (NUM_ATTEMPTS-1) - 1)  /  (BACKOFF_MULTIPLIER-1)
	 * For INITIAL_BACKOFF_MILLIS=500msec, BACKOFF_MULTIPLIER=2, NUM_ATTEMPTS=7 this is 31.5 sec
	 * 
	 * @param executable
	 * @return
	 * @throws IOException
	 * @throws ServiceException
	 */
	public static <T> T executeWithExponentialBackoff(Executable<T> executable) throws Throwable {
		long backoff = INITIAL_BACKOFF_MILLIS;
		Throwable lastException=null;
		int i = 0;
		while (true) {
			try {
				return executable.execute();
			} catch (SynapseServerException e) {
				if (NO_RETRY_STATUSES.contains(e.getStatusCode())) {
					log.severe("Will not retry: "+exceptionMessage(e)); throw e;					
				}
				lastException=e;
			} catch (Exception e) {
				lastException=e;
			}
			log.warning("Encountered exception on attempt "+i+": "+exceptionMessage(lastException));
			if ((++i)>=NUM_ATTEMPTS) break;
			try {
				Thread.sleep(backoff);
			} catch(InterruptedException e) {
				throw lastException;
			}
			backoff *= BACKOFF_MULTIPLIER;
		}
		log.severe("Exhausted retries. Throwing exception: "+exceptionMessage(lastException));
		throw lastException;
	}

}
