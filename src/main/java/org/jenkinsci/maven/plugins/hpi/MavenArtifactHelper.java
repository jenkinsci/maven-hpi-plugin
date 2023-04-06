package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;

public class MavenArtifactHelper {
    public MavenProject resolvePom(Artifact artifact, MavenSession session, ProjectBuilder builder, MavenProject project) throws ProjectBuildingException {
        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProcessPlugins(false); // improve performance
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        return builder.build(artifact, buildingRequest).getProject();
    }
    public ArtifactVersion getVersionNumber(Artifact artifact) throws OverConstrainedVersionException {
        return artifact.getSelectedVersion();
    }
}
