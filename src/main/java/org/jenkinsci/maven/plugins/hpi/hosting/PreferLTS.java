package org.jenkinsci.maven.plugins.hpi.hosting;

import org.apache.maven.project.MavenProject;
import org.jenkinsci.maven.plugins.hpi.hosting.VerificationMessage.Severity;

public class PreferLTS implements VerificationRule {
	@Override
	public VerificationMessage validate(MavenProject project) {
		MavenProject jenkinsParent = findJenkinsParentPom(project);
		if (jenkinsParent == null) {
			return new VerificationMessage(
					"Your plugin doesn't seem to have the Jenkins parent pom in its POM hierarchy, "
							+ "it's generally not a normal situation",
					Severity.WARNING);
		}
		System.out.println(jenkinsParent);
		if (jenkinsParent.getVersion().split("\\.").length != 3) {
			return new VerificationMessage("Your plugin does not seem to have a LTS Jenkins release. In general "
					+ "it's preferrable to use a LTS version as parent version.",
					Severity.INFO);
		}
		return null;
	}

	private MavenProject findJenkinsParentPom(MavenProject project) {
		if ("org.jenkins-ci.plugins".equals(project.getGroupId()) && "plugin".equals(project.getArtifactId())) {
			return project;
		}
		MavenProject parent = project.getParent();
		if (parent == null) {
			return null;
		}
		return findJenkinsParentPom(parent);
	}
}
