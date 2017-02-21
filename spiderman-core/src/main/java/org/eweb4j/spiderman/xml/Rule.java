package org.eweb4j.spiderman.xml;

import org.eweb4j.util.xml.AttrTag;

public class Rule {

	@AttrTag
	private String type;
	
	@AttrTag
	private String value;
	
	@AttrTag
	private String httpMethod;
	
	private Model digUrls;
	
	private Model nextPage;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Model getDigUrls() {
		return this.digUrls;
	}

	public void setDigUrls(Model digUrls) {
		this.digUrls = digUrls;
	}

	public Model getNextPage() {
		return nextPage;
	}

	public void setNextPage(Model nextPage) {
		this.nextPage = nextPage;
	}

	public String getHttpMethod() {
		return this.httpMethod;
	}

	public void setHttpMethod(String httpMethod) {
		this.httpMethod = httpMethod;
	}

	@Override
	public String toString() {
		return "Rule [type=" + this.type + ", value=" + this.value
				+ ", httpMethod=" + this.httpMethod + "]";
	}
	
}
