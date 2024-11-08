/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.nio.file.Files
import java.util.jar.Manifest

assert new File(basedir, 'target/classes').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder.class').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder$DescriptorImpl.class').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder.stapler').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/Messages.class').exists();
assert new File(basedir, 'target/verify-it.hpi').exists();
assert new File(basedir, 'target/verify-it.jar').exists();

assert new File(basedir, 'target/generated-sources/localizer/org/jenkinsci/tools/hpi/its/Messages.java').exists();

content = new File(basedir, 'target/generated-sources/localizer/org/jenkinsci/tools/hpi/its/Messages.java').text;
assert content.contains(" holder.format(\"it.msg\");");

assert new File(basedir, 'target/verify-it/META-INF/MANIFEST.MF').exists()

def pattern = /^[a-f0-9]{40}$|^[a-f0-9]{64}$/
Files.newInputStream(new File(basedir, 'target/verify-it/META-INF/MANIFEST.MF').toPath()).withCloseable { is ->
  Manifest manifest = new Manifest(is)
  assert !manifest.getMainAttributes().getValue('Build-Jdk-Spec').isEmpty()
  assert manifest.getMainAttributes().getValue('Created-By').startsWith('Maven Archiver')
  assert manifest.getMainAttributes().getValue('Extension-Name') == null // was provided by Maven 2, but core prefers Short-Name
  assert manifest.getMainAttributes().getValue('Group-Id').equals('org.jenkins-ci.tools.hpi.its')
  assert manifest.getMainAttributes().getValue('Artifact-Id').equals('verify-it')
  assert manifest.getMainAttributes().getValue('Hudson-Version').equals('2.452.4')
  assert manifest.getMainAttributes().getValue('Implementation-Title').equals('MyNewPlugin') // was project.artifactId in previous versions, now project.name
  assert manifest.getMainAttributes().getValue('Implementation-Version').equals('1.0-SNAPSHOT')
  assert manifest.getMainAttributes().getValue('Jenkins-Version').equals('2.452.4')
  assert manifest.getMainAttributes().getValue('Long-Name').equals('MyNewPlugin')
  assert manifest.getMainAttributes().getValue('Manifest-Version').equals('1.0')
  assert manifest.getMainAttributes().getValue('Plugin-Developers').equals('Noam Chomsky:nchomsky:nchomsky@example.com')
  assert manifest.getMainAttributes().getValue('Plugin-License-Name').equals('MIT License')
  assert manifest.getMainAttributes().getValue('Plugin-License-Url').equals('https://opensource.org/licenses/MIT')
  assert manifest.getMainAttributes().getValue('Plugin-ScmConnection').equals('scm:git:https://github.com/jenkinsci/verify-it-plugin.git')
  assert manifest.getMainAttributes().getValue('Plugin-ScmTag').equals('HEAD')
  assert manifest.getMainAttributes().getValue('Plugin-ScmUrl').equals('https://github.com/jenkinsci/verify-it-plugin')
  def matcher = manifest.getMainAttributes().getValue('Implementation-Build') =~ pattern
  assert matcher.matches()
  assert manifest.getMainAttributes().getValue('Plugin-Version').startsWith('1.0-SNAPSHOT')
  assert manifest.getMainAttributes().getValue('Short-Name').equals('verify-it')
  assert manifest.getMainAttributes().getValue('Specification-Title').equals('MyNewPlugin') // was project.description in previous versions, now project.name
  assert manifest.getMainAttributes().getValue('Url').equals('https://github.com/jenkinsci/verify-it-plugin')
}

// TODO add some test on hpi file content

return true;
