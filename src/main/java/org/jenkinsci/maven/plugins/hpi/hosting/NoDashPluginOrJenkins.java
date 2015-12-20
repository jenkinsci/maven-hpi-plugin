package org.jenkinsci.maven.plugins.hpi.hosting;

import org.apache.maven.project.MavenProject;
import org.jenkinsci.maven.plugins.hpi.hosting.VerificationMessage.Severity;

public class NoDashPluginOrJenkins implements VerificationRule {

	@Override
	public VerificationMessage validate(MavenProject project) {
		if (project.getArtifactId().contains("-plugin") || project.getArtifactId().contains("-jenkins")) {
			return new VerificationMessage("Give your plugin a sensible ID which does not include -jenkins or -plugin",
					Severity.REQUIRED);
		}
		return null;
	}
}
