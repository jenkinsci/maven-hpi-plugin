package org.jenkinsci.maven.plugins.hpi.hosting;

import org.apache.maven.project.MavenProject;

public interface VerificationRule {
	/**
	 * Returns a {@link VerificationMessage} instance if a problem was found, null if not.
	 *
	 * @return a {@link VerificationMessage} instance if a problem was found, null if not.
	 */
	VerificationMessage validate(MavenProject project);
}
