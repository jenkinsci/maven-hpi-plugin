/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;

import java.io.File;
import java.util.Arrays;

/**
 * Builds a new Blue Ocean extension plugin template.
 */
@Mojo(name="createboext", requiresProject = false)
public class CreateBOExtMojo extends AbstractCreateMojo {

    private static final String JENKINS_VERSION = "2.7.3";
    private static final String NODE_VERSION = "6.4.0";
    private static final String NPM_VERSION = "3.10.3";

    @Parameter(property = "blueOceanVersion")
    String blueOceanVersion;

    public void execute() throws MojoExecutionException {
        if(blueOceanVersion == null) {
            try {
                blueOceanVersion = prompter.prompt("Enter the Blue Ocean version");
            } catch (PrompterException e) {
                throw new MojoExecutionException("Failed to create a new Jenkins plugin",e);
            }
        }

        super.execute();

        try {
            File outDir = getOutDir();
            File srcMainDir = new File(outDir, "src" + sep + "main");

            // Remove the Java resources
            FileUtils.deleteDirectory(new File(srcMainDir, "java"));

            File jsDir = new File(srcMainDir, "js");
            File lessDir = new File(srcMainDir, "less");
            File imgDir = new File(lessDir, "images");

            copyTextResources(Arrays.asList("package.json", "gulpfile.js"), "/archetype-resources/", outDir);
            copyTextResources(Arrays.asList("jenkins-js-extension.yaml", "Usain.jsx"), "/archetype-resources/src/main/js/", jsDir);
            copyTextResources(Arrays.asList("extensions.less"), "/archetype-resources/src/main/less/", lessDir);
            copyBinaryResources(Arrays.asList("running.gif", "finished.gif"), "/archetype-resources/src/main/less/images/", imgDir);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create a new Jenkins plugin",e);
        }
    }

    @Override
    protected String getPropertiesPOMFrag() {
        return String.format("<!-- Baseline Jenkins version you use to build the plugin. Users must have this version or newer to run. -->\n" +
                "    <jenkins.version>%s</jenkins.version>\n" +
                "    <!-- Node and NPM versions. -->\n" +
                "    <node.version>%s</node.version>\n" +
                "    <npm.version>%s</npm.version>",
                JENKINS_VERSION,
                NODE_VERSION,
                NPM_VERSION);
    }

    @Override
    protected String getDependenciesPOMFrag() {
        return String.format("<dependencies>\n" +
                "    <dependency>\n" +
                "      <groupId>io.jenkins.blueocean</groupId>\n" +
                "      <artifactId>blueocean</artifactId>\n" +
                "      <version>%s</version>\n" +
                "    </dependency>\n" +
                "  </dependencies>\n", blueOceanVersion);
    }
}
