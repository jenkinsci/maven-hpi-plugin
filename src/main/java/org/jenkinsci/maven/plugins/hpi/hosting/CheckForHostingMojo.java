package org.jenkinsci.maven.plugins.hpi.hosting;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * Checks a plugin for hosting (artifactId name, etc.)
 */
@Mojo(name = "check-for-hosting", requiresProject = true)
public class CheckForHostingMojo extends AbstractMojo {
	private static final String HOSTING_WIKI_PAGE = "https://wiki.jenkins-ci.org/display/JENKINS/Hosting+Plugins";

	private static final String HOSTING_JIRA_PROJECT = "https://issues.jenkins-ci.org/browse/HOSTING";

	@Component
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		// TODO : inject?
		List<VerificationRule> rules = new ArrayList<VerificationRule>();
		rules.add(new NoDashPluginOrJenkins());
		rules.add(new SpecifyLicense());
		rules.add(new PreferLTS());

		List<VerificationMessage> validationMessages = new ArrayList<VerificationMessage>();
		for (VerificationRule rule : rules) {
			VerificationMessage validation = rule.validate(project);
			if (validation != null) {
				validationMessages.add(validation);
			}
		}

		displayMessages(validationMessages);
	}

	private void displayMessages(List<VerificationMessage> validationMessages) {
		if (!validationMessages.isEmpty()) {
			getLog().info("There were some issues found during the verification of your plugin. Please refer to "
					+ HOSTING_JIRA_PROJECT
					+ " and/or the jenkinsci-dev ML to get help if you don't understand the following message(s).");
		}
		else {
			getLog().info("No issue found for hosting");
		}
		// TODO : sort by severity?
		for (VerificationMessage validationMessage : validationMessages) {
			getLog().info(validationMessage.getSeverity() + " => " + validationMessage.getMessage());
		}
	}
}
