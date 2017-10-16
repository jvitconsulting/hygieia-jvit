package com.capitalone.dashboard.model;


public class CommitResponse {

	protected String scmUrl;
	protected String scmCommitLog;
	protected String scmAuthor;
	protected long scmCommitTimestamp;
	protected long numberOfChanges;
	private String compName;

	public CommitResponse() {

	}

	public String getScmUrl() {
		return scmUrl;
	}

	public void setScmUrl(String scmUrl) {
		this.scmUrl = scmUrl;
	}

	public String getScmCommitLog() {
		return scmCommitLog;
	}

	public void setScmCommitLog(String scmCommitLog) {
		this.scmCommitLog = scmCommitLog;
	}

	public String getScmAuthor() {
		return scmAuthor;
	}

	public void setScmAuthor(String scmAuthor) {
		this.scmAuthor = scmAuthor;
	}

	public long getScmCommitTimestamp() {
		return scmCommitTimestamp;
	}

	public void setScmCommitTimestamp(long scmCommitTimestamp) {
		this.scmCommitTimestamp = scmCommitTimestamp;
	}

	public long getNumberOfChanges() {
		return numberOfChanges;
	}

	public void setNumberOfChanges(long numberOfChanges) {
		this.numberOfChanges = numberOfChanges;
	}

	public void setComponentName(String compName) {
		this.compName = compName;
	}
	
	public String getCompName() {
		return compName;
	}

}
