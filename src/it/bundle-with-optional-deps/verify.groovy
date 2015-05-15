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

assert new File(basedir, 'target/bundle-it/WEB-INF/plugins/ssh-credentials.hpi').exists();
assert new File(basedir, 'target/bundle-it/WEB-INF/plugins/credentials.hpi').exists();
assert new File(basedir, 'target/bundle-it/WEB-INF/optional-plugins/ssh-slaves.hpi').exists();
assert new File(basedir, 'target/bundle-it/WEB-INF/optional-plugins/support-core.hpi').exists();
assert new File(basedir, 'target/bundle-it/WEB-INF/optional-plugins/metrics.hpi').exists();

String manifest = new File(basedir, 'target/plugin-manifest.txt').text

assert manifest =~ /ssh-credentials\s+1\.10/ : "direct non-optional dependency on ssh-credentials"
assert manifest =~ /credentials\s+1\.16\.1/ : "transitive non-optional dependency of ssh-credentials"
assert manifest =~ /ssh-slaves\s+1\.6/ : "direct optional dependency on ssh-slaves"
assert ! (manifest =~ /credentials\s+1\.9\.4/) : "don't pull in the transitive dependency from ssh-slaves as already pulled in"
assert manifest =~ /support-core\s+2\.18/ : "direct optional dependency on support-core"
assert manifest =~ /metrics\s+3\.0\.0/ : "transitive optional dependency on metrics"

return true;
