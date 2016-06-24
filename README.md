Maven plugin to build Jenkins plugins.
See the [Extend Jenkins](https://wiki.jenkins-ci.org/display/JENKINS/Extend+Jenkins) wiki page for details.

[Mojo documentation](http://jenkinsci.github.io/maven-hpi-plugin/)

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/)

Before releasing, to sanity test, try

```bash
mvn -Prun-its clean install
# Find some plugin using the 2.x parent POM and run:
mvn -f ../some-plugin -Dhpi-plugin.version=1.XXX-SNAPSHOT -Denforcer.fail=false -DskipTests clean package hpi:run
```


## Updating Jetty
`hpi:run` mojo is a variant of `jetty:run` mojo, and because of the way plugin descriptor is generated, this module copies some code from Jetty Maven plugin, specifically `AbstractJettyMojo.java` and `ConsoleScanner.java`.

To keep upstream tracking easier, prestine copies of these files are copied into `incoming-x.y` branch, then package renamed. This version specific incoming branch is then "theirs" merged into the `incoming` branch, which acts as the upstream tracking branch.

This branch is then merged into `master` via `git merge -X ignore-space-at-eol incoming`. See diff between `incoming` and `master` on these files to see the exact local patches.
