import org.apache.commons.io.FileUtils

import java.nio.charset.Charset

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

for (String line : FileUtils.readLines(new File(basedir, 'build.log'), Charset.defaultCharset())) {
    if (line.contains('Copying snapshot dependency Jenkins plugin')) {
        // ensure contains only JENKINS-70329-dir-two paths
        assert line.matches('.*/target/its/JENKINS-70329-dir-two/sub-module[0-9]/target/test-classes/the.hpl')
    }
}
return true
