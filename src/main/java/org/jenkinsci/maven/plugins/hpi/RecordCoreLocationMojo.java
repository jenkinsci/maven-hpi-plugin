/*
 * Copyright 2015 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jenkinsci.maven.plugins.hpi;

import java.io.IOException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Keeps track of where core was built from, so plugins depending on a snapshot version of core can pick up changes without reload.
 */
@Mojo(name = "record-core-location", defaultPhase = LifecyclePhase.PACKAGE)
public class RecordCoreLocationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Component
    protected PluginWorkspaceMap pluginWorkspaceMap;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (project.getArtifact().isSnapshot()) {
            try {
                pluginWorkspaceMap.write(project.getArtifact().getId(), project.getBasedir());
            } catch (IOException x) {
                throw new MojoExecutionException("failed to write core location", x);
            }
        }
    }
}
