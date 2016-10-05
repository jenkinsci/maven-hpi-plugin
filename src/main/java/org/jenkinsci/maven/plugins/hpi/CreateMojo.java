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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.util.Arrays;

/**
 * Builds a new plugin template.
 * <p>
 * Most of this is really just a rip-off from the {@code archetype:create} goal,
 * but since Maven doesn't really let one Mojo calls another Mojo, this turns
 * out to be the easiest.
 *
 */
@Mojo(name="create", requiresProject = false)
public class CreateMojo extends AbstractCreateMojo {

    public void execute() throws MojoExecutionException {
        super.execute();

        try {
            File viewDir = new File(getOutDir(), "src"+sep+"main"+sep+"resources"+sep+packageName.replace('.',sep)+sep+"HelloWorldBuilder" );

            copyTextResources(Arrays.asList("config.jelly","global.jelly","help-name.html","help-useFrench.html"),
                    "/archetype-resources/src/main/resources/HelloWorldBuilder/", viewDir);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create a new Jenkins plugin",e);
        }
    }
}
