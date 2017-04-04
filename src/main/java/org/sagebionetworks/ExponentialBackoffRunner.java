package org.sagebionetworks;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.apache.http.HttpStatus;
import org.sagebionetworks.client.exceptions.SynapseServerException;

import com.amazonaws.services.lambda.model.ServiceException;

public class ExponentialBackoffRunner {
	private static Logger log = Logger.getLogger(ExponentialBackoffRunner.class.getName());

	public static int DEFAULT_NUM_RETRY_ATTEMPTS = 8; // 63 sec
	private static int NUM_503_RETRY_ATTEMPTS = 16; // 272 min (4h:32m)
	private static long INITIAL_BACKOFF_MILLIS = 500L;
	private static long BACKOFF_MULTIPLIER = 2L;
	
	private int numRetryAttempts;
	private List<Integer> noRetryStatuses  = null;
	
	public ExponentialBackoffRunner(List<Integer> noRetryStatuses, int numRetryAttempts) {
		this.noRetryStatuses=noRetryStatuses;
		this.numRetryAttempts=numRetryAttempts;
	}
	
	public ExponentialBackoffRunner() {
		this.noRetryStatuses=Collections.EMPTY_LIST;
		this.numRetryAttempts=DEFAULT_NUM_RETRY_ATTEMPTS;
	}
	
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
	public <T> T execute(Executable<T> executable) throws Throwable {
		long backoff = INITIAL_BACKOFF_MILLIS;
		Throwable lastException=null;
		int i = 0;
		while (true) {
			Integer statusCode = null;
			try {
				return executable.execute();
			} catch (SynapseServerException e) {
				statusCode=e.getStatusCode();
				if (noRetryStatuses.contains(statusCode)) {
					log.severe("Will not retry: "+exceptionMessage(e)); 
					throw e;					
				}
				lastException=e;
			} catch (Exception e) {
				lastException=e;
			}
			log.warning("Encountered exception on attempt "+i+": "+exceptionMessage(lastException));
			i++;
			if (statusCode!=null && HttpStatus.SC_SERVICE_UNAVAILABLE==statusCode) {
				if (i>=NUM_503_RETRY_ATTEMPTS) break;				
			} else {
				if (i>=numRetryAttempts) break;
			}
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
