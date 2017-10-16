package com.capitalone.dashboard.model;

import java.util.List;

import com.capitalone.dashboard.model.deploy.Environment;

public class DeployComponentResponse {

	private String compName;
	private List<Environment> environments;

	public void setName(String compName) {
		this.compName = compName;
	}

	public void setEnvironments(List<Environment> environments) {
		this.environments = environments;
	}

	public String getCompName() {
		return compName;
	}

	public List<Environment> getEnvironments() {
		return environments;
	}

}
