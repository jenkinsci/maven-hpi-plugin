Maven plugin to build Jenkins plugins.
See the [Extend Jenkins](https://wiki.jenkins-ci.org/display/JENKINS/Extend+Jenkins) wiki page for details.

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/)

Before releasing, to sanity test, try

```bash
mvn -Prun-its clean install
# Edit ../some-plugin/pom.xml to specify <version>1.nnn-SNAPSHOT</version> of this plugin, then:
mvn -f ../some-plugin clean package hpi:run
```
