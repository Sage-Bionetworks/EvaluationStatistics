package org.sagebionetworks;

import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

public class UserSubmissionStats {
	private String name;
	private int submissionCount;
	private int scoredCount;
	private int invalidCount;
	private Date lastSubmission;
	private Set<String> teams;
	
	public UserSubmissionStats(String name) {
		setName(name);
		setTeams(new TreeSet<String>());
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getSubmissionCount() {
		return submissionCount;
	}

	public void setSubmissionCount(int submissionCount) {
		this.submissionCount = submissionCount;
	}

	public int getScoredCount() {
		return scoredCount;
	}

	public void setScoredCount(int scoredCount) {
		this.scoredCount = scoredCount;
	}

	public int getInvalidCount() {
		return invalidCount;
	}

	public void setInvalidCount(int invalidCount) {
		this.invalidCount = invalidCount;
	}

	public Date getLastSubmission() {
		return lastSubmission;
	}

	public void setLastSubmission(Date lastSubmission) {
		this.lastSubmission = lastSubmission;
	}

	public Set<String> getTeams() {
		return teams;
	}

	public void setTeams(Set<String> teams) {
		this.teams = teams;
	}

	

}
