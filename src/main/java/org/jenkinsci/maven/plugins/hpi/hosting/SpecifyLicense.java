package org.jenkinsci.maven.plugins.hpi.hosting;

import org.apache.maven.project.MavenProject;
import org.jenkinsci.maven.plugins.hpi.hosting.VerificationMessage.Severity;

public class SpecifyLicense implements VerificationRule {
	@Override
	public VerificationMessage validate(MavenProject project) {
		if (project.getLicenses().isEmpty()) {
			return new VerificationMessage("Specify an open source license for your code (most plugins use MIT)",
					Severity.REQUIRED);
		}
		return null;
	}
}
