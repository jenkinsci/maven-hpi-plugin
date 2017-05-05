package org.jenkinsci.maven.plugins.hpi.hosting;

// Data Structure
public class VerificationMessage {
	public static enum Severity {
		INFO, WARNING, REQUIRED
	}

	private final String message;

	private final VerificationMessage.Severity severity;

	public VerificationMessage(String message, VerificationMessage.Severity severity) {
		super();
		this.message = message;
		this.severity = severity;
	}

	public String getMessage() {
		return message;
	}

	public VerificationMessage.Severity getSeverity() {
		return severity;
	}
}
