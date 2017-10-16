package com.capitalone.dashboard.model;

import java.util.ArrayList;
import java.util.List;

public class CommitComponentResponse {

	protected String compName;
	protected List<CommitResponse> resCommits = new ArrayList<CommitResponse>();
	protected String topContributor;
	private int topContribution;
	
	public CommitComponentResponse() {
	}
	
	public String getTopContributor() {
		return topContributor;
	}
	
	public void setTopContributor(String topContributor) {
		this.topContributor = topContributor;
	}
	
	public void setCompName(String compName) {
		this.compName = compName;
	}
	
	public void setResCommit(List<CommitResponse> rescom) {
		this.resCommits = rescom;
	}
	
	public String getCompName() {
		return compName;
	}
	
	public List<CommitResponse> getResCommits() {
		return resCommits;
	}

	public void setTopContribution(int topContribution) {
		this.topContribution = topContribution;
	}
	
	public int getTopContribution() {
		return topContribution;
	}
	

}
