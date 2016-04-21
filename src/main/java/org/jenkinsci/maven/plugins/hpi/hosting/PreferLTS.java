package org.jenkinsci.maven.plugins.hpi.hosting;

import org.apache.maven.project.MavenProject;
import org.jenkinsci.maven.plugins.hpi.hosting.VerificationMessage.Severity;

public class PreferLTS implements VerificationRule {
	@Override
	public VerificationMessage validate(MavenProject project) {
		MavenProject jenkinsParent = findPluginParentPom(project);
		if (jenkinsParent == null) {
			return new VerificationMessage(
					"Your plugin doesn't seem to have the Jenkins parent pom in its POM hierarchy. "
							+ "Jenkins plugins generally inherit from the Jenkins parent POM.",
					Severity.WARNING);
		}
		String jenkinsVersion = jenkinsParent.getVersion();

		// In 2.x plugin-pom became a standalone project https://github.com/jenkinsci/plugin-pom
		// In that case, the Jenkins version is specified in the property "jenkins.version".
		if(jenkinsVersion.startsWith("2."))
		{
			jenkinsVersion = (String) project.getProperties().get("jenkins.version");
		}
		if (jenkinsVersion.split("\\.").length != 3) {
			return new VerificationMessage("Your plugin does not seem to have a LTS Jenkins release. In general, "
					+ "it's preferable to use an LTS version as parent version.",
					Severity.INFO);
		}
		return null;
	}

	private MavenProject findPluginParentPom(MavenProject project) {
		if ("org.jenkins-ci.plugins".equals(project.getGroupId()) && "plugin".equals(project.getArtifactId())) {
			return project;
		}
		MavenProject parent = project.getParent();
		if (parent == null) {
			return null;
		}
		return findPluginParentPom(parent);
	}
}
