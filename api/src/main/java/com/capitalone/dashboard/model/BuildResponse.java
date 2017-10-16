package com.capitalone.dashboard.model;

public class BuildResponse {

	private String status;
	private String buildUrl;
	private String startedBy;
	private String number;
	private long duration;
	private String compName;

	public void setBuildStatus(String status) {
		this.status = status;
	}

	public String getStatus() {
		return status;
	}

	public void setBuildUrl(String buildUrl) {
		this.buildUrl = buildUrl;
	}

	public String getBuildUrl() {
		return buildUrl;
	}

	public String getNumber() {
		return number;
	}

	public String getCompName() {
		return compName;
	}

	public long getDuration() {
		return duration;
	}

	public String getStartedBy() {
		return startedBy;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	public void setStartedBy(String startedBy) {
		this.startedBy = startedBy;
	}

	public void setComponentName(String compName) {
		this.compName = compName;
	}

}
