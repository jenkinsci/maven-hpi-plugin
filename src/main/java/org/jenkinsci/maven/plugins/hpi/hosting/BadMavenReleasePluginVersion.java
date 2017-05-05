package org.jenkinsci.maven.plugins.hpi.hosting;

import java.util.List;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.jenkinsci.maven.plugins.hpi.hosting.VerificationMessage.Severity;

/**
 * <p>
 * Prior to the maven-release-plugin 2.5, <a href="https://issues.apache.org/jira/browse/MRELEASE-812">only snapshots would get pushed to
 * remote repositories under some situations</a>.
 * </p>
 *
 * <p>
 * This has been fixed in the Jenkins parent pom with
 * <a href="https://github.com/jenkinsci/pom/commit/62bd9a118493a16ae6bc8eef715785f06a6303be">62bd9a118493a16ae6bc8eef715785f06a6303be</a>
 * included in Jenkins parent pom v1.34.
 * </p>
 *
 * <p>
 * This fix was then propagated the Jenkins plugins parent pom with
 * <a href="https://github.com/jenkinsci/jenkins/commit/4f27f4cc263392a435937941645dc43670fe8ee4">4f27f4cc263392a435937941645dc43670fe8ee4
 * </a> hence starting with <strong>Jenkins 1.576</strong>.
 * </p>
 */
public class BadMavenReleasePluginVersion implements VerificationRule {

	private static final String GROUP_ID = "org.apache.maven.plugins";
	private static final String ARTIFACT_ID = "maven-release-plugin";

	private static final String MIN_RELEASE_PLUGIN_VERSION_STR = "2.5";
	private static final ArtifactVersion MIN_RELEASE_PLUGIN_VERSION = new DefaultArtifactVersion(MIN_RELEASE_PLUGIN_VERSION_STR);

	@Override
	public VerificationMessage validate(MavenProject project) {

		Plugin releasePlugin = getReleasePlugin(project.getBuildPlugins());
		if (releasePlugin == null) {
			return new VerificationMessage("No maven-release-plugin found in the effective-pom. This is, well, unusual.", Severity.WARNING);
		}

		DefaultArtifactVersion usedReleasePluginVersion = new DefaultArtifactVersion(releasePlugin.getVersion());
		if (MIN_RELEASE_PLUGIN_VERSION.compareTo(usedReleasePluginVersion) > 0) {

			return new VerificationMessage(
					"You're using a maven-release-plugin version < " + MIN_RELEASE_PLUGIN_VERSION_STR
							+ ". You're likely to have issues when releasing. "
							+ "Please force it to a newer version in your pom.xml, or upgrade to Jenkins 1.580.1 or above "
							+ "(1.576 to be exact, but LTS are recommended)",
					Severity.WARNING);
		}

		return null;
	}

	private Plugin getReleasePlugin(List<Plugin> buildPlugins) {
		for (Plugin plugin : buildPlugins) {
			if (GROUP_ID.equals(plugin.getGroupId()) && ARTIFACT_ID.equals(plugin.getArtifactId())) {
				return plugin;
			}
		}
		return null;
	}
}
