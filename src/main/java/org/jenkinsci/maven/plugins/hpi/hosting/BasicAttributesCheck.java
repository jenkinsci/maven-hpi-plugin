package org.jenkinsci.maven.plugins.hpi.hosting;

import org.apache.maven.project.MavenProject;
import org.jenkinsci.maven.plugins.hpi.hosting.VerificationMessage.Severity;

public class BasicAttributesCheck implements VerificationRule {

	@Override
	public VerificationMessage validate(MavenProject project) {

		if (containsJenkinsOrPlugin(project.getArtifactId(), true)) {
			return new VerificationMessage("Give your plugin a sensible <artifactId> which does not include -jenkins or -plugin.",
					Severity.REQUIRED);
		}
		// artifactId is not nullable, so not checking it
		if (isEmpty(project.getName())) {
			return new VerificationMessage(
					"Give your plugin a sensible (non empty) <name> (that does not include neither Jenkins nor Plugin).",
					Severity.REQUIRED);
		}
		if (containsJenkinsOrPlugin(project.getName(), false)) {
			return new VerificationMessage("Give your plugin a sensible <name> which does not include jenkins or plugin.",
					Severity.REQUIRED);
		}
		return null;
	}

	private boolean isEmpty(String string) {
		if (string == null || "".equals(string.trim())) {
			return true;
		}
		return false;
	}

	private boolean containsJenkinsOrPlugin(String string, boolean includeDash) {
		string = string.toLowerCase();
		String plugin = "plugin";
		String jenkins = "jenkins";
		if (includeDash) {
			plugin = "-" + plugin;
			jenkins = "-" + jenkins;
		}
		return string.contains("-plugin") || string.contains("-jenkins");
	}
}
