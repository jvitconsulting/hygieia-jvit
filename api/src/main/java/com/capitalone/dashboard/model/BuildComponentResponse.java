package com.capitalone.dashboard.model;

import java.util.ArrayList;
import java.util.List;

public class BuildComponentResponse {

	protected String compName;
	protected List<BuildResponse> buildResArray = new ArrayList<BuildResponse>();

	public BuildComponentResponse() {
	}

	public List<BuildResponse> getBuildResArray() {
		return buildResArray;
	}

	public void setBuildResArray(List<BuildResponse> buildResArray) {
		this.buildResArray = buildResArray;
	}

	public void setCompName(String compName) {
		this.compName = compName;
	}

	public String getCompName() {
		return compName;
	}

}
