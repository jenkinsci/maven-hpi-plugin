import java.util.zip.ZipInputStream

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

assert new File(basedir, 'target/classes').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder.class').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder$DescriptorImpl.class').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/HelloWorldBuilder.stapler').exists();
assert new File(basedir, 'target/classes/org/jenkinsci/tools/hpi/its/Messages.class').exists();
assert !new File(basedir, 'target/process-jar/WEB-INF/lib/jackson-annotations-2.6.0.jar').exists();
assert !new File(basedir, 'target/process-jar/WEB-INF/lib/jackson-core-2.6.3.jar').exists();
assert !new File(basedir, 'target/process-jar/WEB-INF/lib/jackson-databind-2.6.3').exists();
assert new File(basedir, 'target/process-jar.hpi').exists();
assert new File(basedir, 'target/process-jar.jar').exists();

assert new File(basedir, 'target/generated-sources/localizer/org/jenkinsci/tools/hpi/its/Messages.java').exists();

content = new File(basedir, 'target/generated-sources/localizer/org/jenkinsci/tools/hpi/its/Messages.java').text;
assert content.contains(" holder.format(\"it.msg\");");

File.metaClass.unzip = { File outputFolder ->
    byte[] buffer = new byte[8192];
    def zis = new ZipInputStream(new FileInputStream(delegate));
    try {
        def ze = zis.getNextEntry();
        try {
            while (ze != null) {
                File newFile = new File(outputFolder, ze.getName());
                newFile.getParentFile().mkdirs();
                if (ze.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                ze = zis.getNextEntry();
            }
        } finally {
            zis.closeEntry();
        }
    } finally {
        zis.close();
    }
}

new File(basedir, "target/verify-hpi").deleteDir()
new File(basedir, "target/verify-jar").deleteDir()
assert new File(basedir, 'target/process-jar.hpi').exists();
new File(basedir, "target/process-jar.hpi").unzip(new File(basedir, "target/verify-hpi"))
assert new File(basedir, 'target/verify-hpi/WEB-INF/lib/process-jar.jar').exists();

new File(basedir, "target/verify-hpi/WEB-INF/lib/process-jar.jar").unzip(new File(basedir, "target/verify-jar"));
assert new File(basedir, 'target/verify-jar/META-INF/KEY.SF').exists();

return true;
