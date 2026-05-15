package org.jenkinsci.maven.plugins.hpi.enforcer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;

/**
 * Maven Enforcer rule that bans obsolete dependency and property overrides.
 * <p>
 * This rule fails the build when {@code <dependencyManagement>} specifies versions for dependencies that are
 * older than or equal to versions provided by imported BOMs, or when {@code <properties>} override {@code *.version}
 * properties with values older than or equal to those in the parent POM. This helps identify unnecessary overrides
 * that can be removed when updating BOM versions or parent POMs.
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
 *               <banObsoleteDependencyOverrides/>
 *             </rules>
 *           </configuration>
 *         </execution>
 *       </executions>
 *     </plugin>
 *   </plugins>
 * </build>
 * }</pre>
 */
@Named("banObsoleteDependencyOverrides")
public class BanObsoleteDependencyOverrides extends AbstractEnforcerRule {

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
     * <banObsoleteDependencyOverrides>
     *   <ignorePatterns>
     *     <ignorePattern>junit:junit:jar</ignorePattern>
     *     <ignorePattern>org.mockito:mockito-core:jar</ignorePattern>
     *   </ignorePatterns>
     * </banObsoleteDependencyOverrides>
     * }</pre>
     */
    private List<String> ignorePatterns;

    @Inject
    public BanObsoleteDependencyOverrides(MavenProject project, BomResolverUtil bomResolverUtil) {
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
            getLog().info("banObsoleteDependencyOverrides skipped");
            return;
        }

        // Phase 1: Check dependencyManagement section
        List<ObsoleteOverride> violations = new ArrayList<>();
        DependencyManagement depMgmt = project.getOriginalModel().getDependencyManagement();

        if (depMgmt != null && depMgmt.getDependencies() != null) {
            // Phase 1a: Identify imported BOMs
            List<Dependency> importedBoms = new ArrayList<>();
            for (Dependency dep : depMgmt.getDependencies()) {
                if ("pom".equals(dep.getType()) && "import".equals(dep.getScope())) {
                    importedBoms.add(dep);
                }
            }

            if (!importedBoms.isEmpty()) {
                getLog().debug("[BanObsoleteDependencyOverrides] Found " + importedBoms.size() + " imported BOM(s)");

                // Phase 2: Resolve BOM managed dependencies
                Map<String, BomResolverUtil.BomManagedDependency> bomDependencies = new LinkedHashMap<>();
                for (Dependency bomDep : importedBoms) {
                    try {
                        String resolvedGroupId =
                                bomResolverUtil.resolveProperties(getLog(), bomDep.getGroupId(), project);
                        String resolvedArtifactId =
                                bomResolverUtil.resolveProperties(getLog(), bomDep.getArtifactId(), project);
                        String resolvedVersion =
                                bomResolverUtil.resolveProperties(getLog(), bomDep.getVersion(), project);
                        Map<String, BomResolverUtil.BomManagedDependency> resolved =
                                bomResolverUtil.resolveBomManagedDependencies(getLog(), bomDep, project);
                        getLog().debug("[BanObsoleteDependencyOverrides] Resolved BOM: " + resolvedGroupId + ":"
                                + resolvedArtifactId + ":" + resolvedVersion + " with " + resolved.size()
                                + " managed dependencies");
                        // Later BOMs override earlier ones
                        bomDependencies.putAll(resolved);
                    } catch (Exception e) {
                        getLog().warn("[BanObsoleteDependencyOverrides] Failed to resolve BOM "
                                + bomDep.getGroupId() + ":" + bomDep.getArtifactId() + ":" + bomDep.getVersion()
                                + ": " + e.getMessage());
                    }
                }

                if (!bomDependencies.isEmpty()) {
                    // Phase 3: Check project's direct dependencyManagement entries
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
                            getLog().debug("[BanObsoleteDependencyOverrides] Skipping " + key
                                    + " (matches ignorePatterns)");
                            continue;
                        }

                        BomResolverUtil.BomManagedDependency bomDep = bomDependencies.get(key);
                        if (bomDep == null) {
                            continue;
                        }

                        try {
                            // Resolve property placeholders
                            String declaredVersion =
                                    bomResolverUtil.resolveProperties(getLog(), dep.getVersion(), project);
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
                            getLog().warn("[BanObsoleteDependencyOverrides] Failed to compare versions for "
                                    + key
                                    + ": declared=" + dep.getVersion() + ", BOM=" + bomDep.version() + ": "
                                    + e.getMessage());
                        }
                    }
                }
            }
        }

        // Phase 4: Check for obsolete property overrides (e.g., jenkins.version)
        List<ObsoletePropertyOverride> propertyViolations = new ArrayList<>();
        MavenProject parentProject = project.getParent();
        if (parentProject != null) {
            Properties declaredProps = project.getOriginalModel().getProperties();
            Properties parentProps = parentProject.getProperties();

            for (String propName : declaredProps.stringPropertyNames()) {
                // Only check *.version properties
                if (!propName.endsWith(".version")) {
                    continue;
                }

                String declaredValue = declaredProps.getProperty(propName);
                String parentValue = parentProps.getProperty(propName);

                // Skip if parent doesn't define this property
                if (parentValue == null) {
                    continue;
                }

                // Resolve properties in declared value (e.g., ${jenkins.baseline}.3)
                String resolvedDeclaredValue;
                try {
                    resolvedDeclaredValue = bomResolverUtil.resolveProperties(getLog(), declaredValue, project);
                } catch (IllegalStateException e) {
                    getLog().debug("[BanObsoleteDependencyOverrides] Could not resolve properties in " + propName + "="
                            + declaredValue + ", skipping comparison: " + e.getMessage());
                    continue;
                }

                // Compare versions (flag if declared <= parent)
                try {
                    ComparableVersion declared = new ComparableVersion(resolvedDeclaredValue);
                    ComparableVersion parent = new ComparableVersion(parentValue);

                    if (declared.compareTo(parent) <= 0) {
                        propertyViolations.add(
                                new ObsoletePropertyOverride(propName, resolvedDeclaredValue, parentValue));
                    }
                } catch (Exception e) {
                    getLog().debug("[BanObsoleteDependencyOverrides] Failed to compare property versions for "
                            + propName + ": declared=" + resolvedDeclaredValue + ", parent=" + parentValue + ": "
                            + e.getMessage());
                }
            }
        }

        // Phase 5: Report all violations together
        if (!violations.isEmpty() || !propertyViolations.isEmpty()) {
            StringBuilder message = new StringBuilder();

            if (!violations.isEmpty()) {
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
            }

            if (!propertyViolations.isEmpty()) {
                if (!violations.isEmpty()) {
                    message.append("\n");
                }

                message.append("""
                        Found obsolete property overrides.

                        The following *.version properties are set to older or equal versions compared to the parent POM.
                        These overrides may be unnecessary and should be reviewed:

                        """);

                for (ObsoletePropertyOverride violation : propertyViolations) {
                    message.append("  - ")
                            .append(violation.propertyName)
                            .append(": declared ")
                            .append(violation.declaredValue);

                    if (violation.declaredValue.equals(violation.parentValue)) {
                        message.append(" == ");
                    } else {
                        message.append(" < ");
                    }

                    message.append(violation.parentValue).append(" from parent POM\n");
                }
            }

            message.append("""

                    To fix this issue:
                      1. Remove the obsolete overrides from <dependencyManagement> and/or <properties>, OR
                      2. Update them to versions newer than what the BOM/parent provides, OR
                      3. <banObsoleteDependencyOverrides.skip>true</banObsoleteDependencyOverrides.skip>

                    See https://www.jenkins.io/doc/developer/plugin-development/dependency-management/ for more information.""");

            throw new EnforcerRuleException(message.toString());
        }

        getLog().debug("[BanObsoleteDependencyOverrides] No obsolete overrides found");
    }

    private record ObsoleteOverride(
            String groupId, String artifactId, String declaredVersion, String bomVersion, String bomArtifactId) {}

    private record ObsoletePropertyOverride(String propertyName, String declaredValue, String parentValue) {}
}
