package com.webobjects.foundation.xml;

public final class NSXMLOutputFormat {

	private String encoding;
	private String version;
	private boolean indenting;
	private boolean omitXMLDeclaration;

	public NSXMLOutputFormat() {
		this(true);
	}

	public NSXMLOutputFormat(final boolean on) {
		indenting = on;
		encoding = "UTF-8";
		version = "1.0";
		// not sure what the default was here
		omitXMLDeclaration = false;
	}

	public String encoding() {
		return encoding;
	}

	public void setEncoding(final String encoding) {
		this.encoding = encoding;
	}

	public boolean indenting() {
		return indenting;
	}

	public void setIndenting(final boolean on) {
		indenting = on;
	}

	public String version() {
		return version;
	}

	public void setVersion(final String version) {
		this.version = version;
	}

	public boolean omitXMLDeclaration() {
		return omitXMLDeclaration;
	}

	public void setOmitXMLDeclaration(final boolean omit) {
		omitXMLDeclaration = omit;
	}
}