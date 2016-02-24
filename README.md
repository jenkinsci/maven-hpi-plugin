Maven plugin to build Jenkins plugins.
See the [Extend Jenkins](https://wiki.jenkins-ci.org/display/JENKINS/Extend+Jenkins) wiki page for details.

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/)

Before releasing, to sanity test, try

```bash
mvn -Prun-its clean install
# Edit ../some-plugin/pom.xml to specify <version>1.nnn-SNAPSHOT</version> of this plugin, then:
mvn -f ../some-plugin clean package hpi:run
```


## Updating Jetty
`hpi:run` mojo is a variant of `jetty:run` mojo, and because of the way plugin descriptor is generated, this module copies some code from Jetty Maven plugin, specifically `AbstractJettyMojo.java` and `ConsoleScanner.java`.

To keep upstream tracking easier, prestine copies of these files are copied into `incoming-x.y` branch, then package renamed. This version specific incoming branch is then "theirs" merged into the `incoming` branch, which acts as the upstream tracking branch.

This branch is then merged into `master` via `git merge -X ignore-space-at-eol incoming`. See diff between `incoming` and `master` on these files to see the exact local patches.
