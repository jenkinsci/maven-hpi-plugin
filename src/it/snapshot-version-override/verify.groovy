import java.util.jar.JarFile

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

assert new File(basedir, 'target/version.txt').exists();
assert new File(basedir, 'target/classes').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder.class').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder$DescriptorImpl.class').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder.stapler').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/Messages.class').exists();
assert new File(basedir, 'target/snapshot-version-override.hpi').exists();
assert new File(basedir, 'target/snapshot-version-override.jar').exists();

assert new File(basedir, 'target/generated-sources/localizer/org/jenkinsci/tools/hpi/its/Messages.java').exists();

def content = new File(basedir, 'target/generated-sources/localizer/org/jenkinsci/tools/hpi/its/Messages.java').text;
assert content.contains(" holder.format(\"it.msg\");");

def expectVersion = new File(basedir, 'target/version.txt').text.trim();

assert !expectVersion.isEmpty() : "contents of target/version.txt, '${expectVersion}' is not empty string";
assert !"1.x-SNAPSHOT".equals(expectVersion) : "contents of target/version.txt, '${expectVersion}' is not 1.x-SNAPSHOT";

def actualVersion
JarFile jf = new JarFile(new File(basedir, 'target/snapshot-version-override.hpi'));
try {
    actualVersion = jf.getManifest().getMainAttributes().getValue("Plugin-Version").replaceFirst(' [(].+[)]$', "");
} finally {
    jf.close();
}
assert expectVersion.equals(actualVersion) : "Manifest Plugin-Version entry: '${actualVersion}' == '${expectVersion}'";

return true;
