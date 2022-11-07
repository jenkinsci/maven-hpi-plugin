package org.jenkinsci.maven.plugins.hpi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.ConflictData;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DirectScopeDependencySelector;
import org.apache.maven.shared.dependency.graph.internal.VerboseJavaScopeSelector;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.eclipse.aether.version.VersionConstraint;

/**
 * <p>
 *     Project dependency raw dependency collector with Jenkins hpi filter need
 * </p>
 * <p>adaptation of some classes from
 * https://github.com/apache/maven-dependency-tree/tree/master/src/main/java/org/apache/maven/shared/dependency/graph/internal
 * </p>
 */
@Named
public class JenkinsHpiDependencyCollector {
    private final RepositorySystem repositorySystem;

    @Inject
    public JenkinsHpiDependencyCollector(RepositorySystem repositorySystem)
    {
        this.repositorySystem = repositorySystem;
    }

    public DependencyNode collectDependencyGraph(ProjectBuildingRequest buildingRequest, ArtifactFilter filter, Log log)
            throws DependencyCollectorBuilderException
    {
        try {
            MavenProject project = buildingRequest.getProject();
            Artifact projectArtifact = project.getArtifact();
            DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(buildingRequest.getRepositorySession());

            DependencyGraphTransformer transformer =
                    new ConflictResolver(new NearestVersionSelector(),
                            new VerboseJavaScopeSelector(),
                            new SimpleOptionalitySelector(),
                            new JavaScopeDeriver());
            session.setDependencyGraphTransformer(transformer);

            DependencySelector depFilter =
                    new AndDependencySelector(new DirectScopeDependencySelector(JavaScopes.TEST),
                            new DirectScopeDependencySelector(JavaScopes.PROVIDED));
            session.setDependencySelector(depFilter);

            session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
            session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

            CollectRequest collectRequest = new CollectRequest();
            org.eclipse.aether.artifact.Artifact aetherArtifact = RepositoryUtils.toArtifact(projectArtifact);
            collectRequest.setRoot(new org.eclipse.aether.graph.Dependency(aetherArtifact, ""));
            List<ArtifactRepository> remoteArtifactRepositories = project.getRemoteArtifactRepositories();
            collectRequest.setRepositories(RepositoryUtils.toRepos(remoteArtifactRepositories));

            org.eclipse.aether.artifact.ArtifactTypeRegistry stereotypes = session.getArtifactTypeRegistry();
            collectDependencyList(collectRequest, project, stereotypes);
            collectManagedDependencyList(collectRequest, project, stereotypes);

            CollectResult collectResult = repositorySystem.collectDependencies(session, collectRequest);

            org.eclipse.aether.graph.DependencyNode rootNode = collectResult.getRoot();

            if (log.isDebugEnabled())
            {
                logTree(rootNode, log);
            }

            return buildDependencyNode(null, rootNode, projectArtifact, filter);

        } catch (DependencyCollectionException e) {
            throw new DependencyCollectorBuilderException(e.getMessage(), e);
        }

    }

    private void logTree(org.eclipse.aether.graph.DependencyNode rootNode, Log log)
    {
        log.debug("--- log tree start ---");
        // print the node tree with its associated data Map
        rootNode.accept(new TreeDependencyVisitor(new DependencyVisitor()
        {
            String indent = "";

            @Override
            public boolean visitEnter(org.eclipse.aether.graph.DependencyNode dependencyNode)
            {
                log.debug(indent + "Aether node: " + dependencyNode + " data map: "+dependencyNode.getData());
                indent += "    ";
                return true;
            }

            @Override
            public boolean visitLeave(org.eclipse.aether.graph.DependencyNode dependencyNode)
            {
                indent = indent.substring(0, indent.length() - 4);
                return true;
            }
        }));
        log.debug("--- log tree end ---");
    }

    private void collectManagedDependencyList(CollectRequest collectRequest, MavenProject project,
                                              ArtifactTypeRegistry stereotypes)
    {
        if (project.getDependencyManagement() != null)
        {
            for (Dependency dependency : project.getDependencyManagement().getDependencies())
            {
                org.eclipse.aether.graph.Dependency aetherDep = RepositoryUtils.toDependency(dependency, stereotypes);
                collectRequest.addManagedDependency(aetherDep);
            }
        }
    }

    private void collectDependencyList(CollectRequest collectRequest, MavenProject project,
                                       org.eclipse.aether.artifact.ArtifactTypeRegistry stereotypes)
    {
        for (Dependency dependency : project.getDependencies())
        {
            org.eclipse.aether.graph.Dependency aetherDep = RepositoryUtils.toDependency(dependency, stereotypes);
            collectRequest.addDependency(aetherDep);
        }
    }

    private Artifact getDependencyArtifact(org.eclipse.aether.graph.Dependency dep)
    {
        org.eclipse.aether.artifact.Artifact artifact = dep.getArtifact();

        Artifact mavenArtifact = RepositoryUtils.toArtifact(artifact);
        mavenArtifact.setScope(dep.getScope());
        mavenArtifact.setOptional(dep.isOptional());

        return mavenArtifact;
    }

    private DependencyNode buildDependencyNode(DependencyNode parent, org.eclipse.aether.graph.DependencyNode node,
                                               Artifact artifact, ArtifactFilter filter)
    {
        String premanagedVersion = DependencyManagerUtils.getPremanagedVersion(node);
        String premanagedScope = DependencyManagerUtils.getPremanagedScope(node);

        Boolean optional = null;
        if (node.getDependency() != null)
        {
            optional = node.getDependency().isOptional();
        }

        List<org.apache.maven.model.Exclusion> exclusions = null;
        if (node.getDependency() != null)
        {
            exclusions = new ArrayList<>(node.getDependency().getExclusions().size());
            for (Exclusion exclusion : node.getDependency().getExclusions())
            {
                org.apache.maven.model.Exclusion modelExclusion = new org.apache.maven.model.Exclusion();
                modelExclusion.setGroupId(exclusion.getGroupId());
                modelExclusion.setArtifactId(exclusion.getArtifactId());
                exclusions.add(modelExclusion);
            }
        }

        org.eclipse.aether.graph.DependencyNode winner =
                (org.eclipse.aether.graph.DependencyNode) node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        String winnerVersion = null;
        String ignoredScope = null;
        if (winner != null)
        {
            winnerVersion = winner.getArtifact().getBaseVersion();
        }
        else
        {
            ignoredScope = (String) node.getData().get(VerboseJavaScopeSelector.REDUCED_SCOPE);
        }

        ConflictData data = new ConflictData(winnerVersion, ignoredScope);

        VerboseDependencyNode current =
                new VerboseDependencyNode(parent, artifact, premanagedVersion, premanagedScope,
                        getVersionSelectedFromRange(node.getVersionConstraint()), optional,
                        exclusions, data);

        List<DependencyNode> nodes = new ArrayList<>(node.getChildren().size());
        for (org.eclipse.aether.graph.DependencyNode child : node.getChildren())
        {
            Artifact childArtifact = getDependencyArtifact(child.getDependency());
            if ((filter == null) || filter.include(childArtifact))
            {
                nodes.add(buildDependencyNode(current, child, childArtifact, filter));
            }
        }
        current.setChildren(Collections.unmodifiableList(nodes));
        return current;
    }

    private String getVersionSelectedFromRange(VersionConstraint constraint)
    {
        if ((constraint == null) || (constraint.getVersion() != null))
        {
            return null;
        }
        return constraint.getRange().toString();
    }

    private static class VerboseDependencyNode
            extends DefaultDependencyNode
    {

        private final ConflictData data;

        VerboseDependencyNode(DependencyNode parent, Artifact artifact, String premanagedVersion,
                              String premanagedScope, String versionConstraint, Boolean optional,
                              List<org.apache.maven.model.Exclusion> exclusions, ConflictData data)
        {
            super(parent, artifact, premanagedVersion, premanagedScope, versionConstraint, optional, exclusions);

            this.data = data;
        }

        @Override
        public String toNodeString()
        {
            StringBuilder buffer = new StringBuilder();

            boolean included = (data.getWinnerVersion() == null);

            if (!included)
            {
                buffer.append('(');
            }

            buffer.append(getArtifact());

            ItemAppender appender = new ItemAppender(buffer, included ? " (" : " - ", "; ", included ? ")" : "");

            if (getPremanagedVersion() != null)
            {
                appender.append("version managed from ", getPremanagedVersion());
            }

            if (getPremanagedScope() != null)
            {
                appender.append("scope managed from ", getPremanagedScope());
            }

            if (data.getOriginalScope() != null)
            {
                appender.append("scope updated from ", data.getOriginalScope());
            }

            if (data.getIgnoredScope() != null)
            {
                appender.append("scope not updated to ", data.getIgnoredScope());
            }

            if (!included)
            {
                String winnerVersion = data.getWinnerVersion();
                if (winnerVersion.equals(getArtifact().getVersion()))
                {
                    appender.append("omitted for duplicate");
                }
                else
                {
                    appender.append("omitted for conflict with ", winnerVersion);
                }
            }

            appender.flush();

            if (!included)
            {
                buffer.append(')');
            }

            return buffer.toString();
        }

        /**
         * Utility class to concatenate a number of parameters with separator tokens.
         */
        private static class ItemAppender
        {
            private StringBuilder buffer;

            private String startToken;

            private String separatorToken;

            private String endToken;

            private boolean appended;

            ItemAppender(StringBuilder buffer, String startToken, String separatorToken, String endToken)
            {
                this.buffer = buffer;
                this.startToken = startToken;
                this.separatorToken = separatorToken;
                this.endToken = endToken;

                appended = false;
            }

            public ItemAppender append(String item1)
            {
                appendToken();

                buffer.append(item1);

                return this;
            }

            public ItemAppender append(String item1, String item2)
            {
                appendToken();

                buffer.append(item1).append(item2);

                return this;
            }

            public void flush()
            {
                if (appended)
                {
                    buffer.append(endToken);

                    appended = false;
                }
            }

            private void appendToken()
            {
                buffer.append(appended ? separatorToken : startToken);

                appended = true;
            }
        }
    }

}