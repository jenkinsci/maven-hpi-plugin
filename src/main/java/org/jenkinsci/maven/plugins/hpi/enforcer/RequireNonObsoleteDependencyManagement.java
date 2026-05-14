package org.jenkinsci.maven.plugins.hpi.enforcer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;

/**
 * Maven Enforcer rule that detects obsolete dependency version overrides in {@code <dependencyManagement>}.
 * <p>
 * This rule fails the build when {@code <dependencyManagement>} specifies versions for dependencies that are
 * older than or equal to versions provided by imported BOMs. This helps identify unnecessary overrides that
 * can be removed when updating BOM versions.
 * </p>
 * <p>
 * Example usage in a POM:
 * </p>
 * <pre>{@code
 * <build>
 *   <plugins>
 *     <plugin>
 *       <artifactId>maven-enforcer-plugin</artifactId>
 *       <executions>
 *         <execution>
 *           <goals><goal>enforce</goal></goals>
 *           <configuration>
 *             <rules>
 *               <requireNonObsoleteDependencyManagement/>
 *             </rules>
 *           </configuration>
 *         </execution>
 *       </executions>
 *     </plugin>
 *   </plugins>
 * </build>
 * }</pre>
 */
@Named("requireNonObsoleteDependencyManagement")
public class RequireNonObsoleteDependencyManagement extends AbstractEnforcerRule {

    private final MavenProject project;
    private final BomResolverUtil bomResolverUtil;

    /**
     * Whether to skip this rule.
     */
    private boolean skip = false;

    /**
     * List of dependencies to ignore when checking for obsolete overrides.
     * Each entry should be a management key (groupId:artifactId:type[:classifier]).
     * <p>
     * Example:
     * <pre>{@code
     * <requireNonObsoleteDependencyManagement>
     *   <ignorePatterns>
     *     <ignorePattern>junit:junit:jar</ignorePattern>
     *     <ignorePattern>org.mockito:mockito-core:jar</ignorePattern>
     *   </ignorePatterns>
     * </requireNonObsoleteDependencyManagement>
     * }</pre>
     */
    private List<String> ignorePatterns;

    @Inject
    public RequireNonObsoleteDependencyManagement(MavenProject project, BomResolverUtil bomResolverUtil) {
        this.project = Objects.requireNonNull(project);
        this.bomResolverUtil = Objects.requireNonNull(bomResolverUtil);
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setIgnorePatterns(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        if (skip) {
            getLog().info("[RequireNonObsoleteDependencyManagement] Skipping rule");
            return;
        }

        // Use the original model to see the raw dependencyManagement before BOM imports are resolved
        DependencyManagement depMgmt = project.getOriginalModel().getDependencyManagement();
        if (depMgmt == null || depMgmt.getDependencies() == null) {
            getLog().info(
                            "[RequireNonObsoleteDependencyManagement] No dependencyManagement section found, skipping rule");
            return;
        }

        // Phase 1: Identify imported BOMs
        List<Dependency> importedBoms = new ArrayList<>();
        for (Dependency dep : depMgmt.getDependencies()) {
            if ("pom".equals(dep.getType()) && "import".equals(dep.getScope())) {
                importedBoms.add(dep);
            }
        }

        if (importedBoms.isEmpty()) {
            getLog().info("[RequireNonObsoleteDependencyManagement] No imported BOMs found, skipping rule");
            return;
        }

        getLog().info("[RequireNonObsoleteDependencyManagement] Found " + importedBoms.size() + " imported BOM(s)");

        // Phase 2: Resolve BOM managed dependencies
        Map<String, BomResolverUtil.BomManagedDependency> bomDependencies = new LinkedHashMap<>();
        for (Dependency bomDep : importedBoms) {
            try {
                Map<String, BomResolverUtil.BomManagedDependency> resolved =
                        bomResolverUtil.resolveBomManagedDependencies(bomDep, project);
                getLog().info("[RequireNonObsoleteDependencyManagement] Resolved BOM: " + bomDep.getGroupId() + ":"
                        + bomDep.getArtifactId() + ":" + bomDep.getVersion() + " with " + resolved.size()
                        + " managed dependencies");
                // Later BOMs override earlier ones
                bomDependencies.putAll(resolved);
            } catch (Exception e) {
                getLog().warn("[RequireNonObsoleteDependencyManagement] Failed to resolve BOM " + bomDep.getGroupId()
                        + ":" + bomDep.getArtifactId() + ":" + bomDep.getVersion() + ": " + e.getMessage());
            }
        }

        if (bomDependencies.isEmpty()) {
            getLog().info(
                            "[RequireNonObsoleteDependencyManagement] No managed dependencies found in BOMs, skipping rule");
            return;
        }

        // Phase 3: Check project's direct dependencyManagement entries
        List<ObsoleteOverride> violations = new ArrayList<>();
        for (Dependency dep : depMgmt.getDependencies()) {
            // Skip BOM imports themselves
            if ("pom".equals(dep.getType()) && "import".equals(dep.getScope())) {
                continue;
            }

            // Skip if no version specified (relying on BOM)
            if (dep.getVersion() == null || dep.getVersion().isEmpty()) {
                continue;
            }

            String key = dep.getManagementKey();

            // Skip if this dependency is in the ignore list
            if (ignorePatterns != null && ignorePatterns.contains(key)) {
                getLog().debug("[RequireNonObsoleteDependencyManagement] Skipping " + key
                        + " (matches ignorePatterns)");
                continue;
            }

            BomResolverUtil.BomManagedDependency bomDep = bomDependencies.get(key);
            if (bomDep == null) {
                continue;
            }

            try {
                // Resolve property placeholders
                String declaredVersion = bomResolverUtil.resolveProperties(getLog(), dep.getVersion(), project);
                String bomVersion = bomDep.version();

                // Compare versions
                ComparableVersion declared = new ComparableVersion(declaredVersion);
                ComparableVersion bom = new ComparableVersion(bomVersion);

                if (declared.compareTo(bom) <= 0) {
                    violations.add(new ObsoleteOverride(
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            declaredVersion,
                            bomVersion,
                            bomDep.bomArtifactId()));
                }
            } catch (Exception e) {
                getLog().warn("[RequireNonObsoleteDependencyManagement] Failed to compare versions for " + key
                        + ": declared=" + dep.getVersion() + ", BOM=" + bomDep.version() + ": " + e.getMessage());
            }
        }

        // Phase 4: Report all violations
        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("""
                    Found obsolete dependency version overrides in <dependencyManagement>.

                    The following dependencies declare versions that are older than or equal to versions
                    provided by imported BOMs. These overrides are unnecessary and should be removed:

                    """);

            for (ObsoleteOverride violation : violations) {
                message.append("  - ")
                        .append(violation.groupId)
                        .append(":")
                        .append(violation.artifactId)
                        .append(": declared ")
                        .append(violation.declaredVersion);

                if (violation.declaredVersion.equals(violation.bomVersion)) {
                    message.append(" == ");
                } else {
                    message.append(" < ");
                }

                message.append(violation.bomVersion)
                        .append(" from imported BOM ")
                        .append(violation.bomArtifactId)
                        .append(" (or a BOM it imports)")
                        .append("\n");
            }

            message.append("""

                    To fix this issue:
                      1. Remove the version specifications from <dependencyManagement> for these dependencies, OR
                      2. Update them to versions newer than what the BOM provides, OR
                      3. <requireNonObsoleteDependencyManagement.skip>true</requireNonObsoleteDependencyManagement.skip>

                    See https://www.jenkins.io/doc/developer/plugin-development/dependency-management/ for more information.""");

            throw new EnforcerRuleException(message.toString());
        }

        getLog().info("[RequireNonObsoleteDependencyManagement] No obsolete overrides found");
    }

    private record ObsoleteOverride(
            String groupId, String artifactId, String declaredVersion, String bomVersion, String bomArtifactId) {}
}
