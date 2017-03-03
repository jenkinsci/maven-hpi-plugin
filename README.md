Maven plugin to build Jenkins plugins.
See the [Extend Jenkins](https://wiki.jenkins-ci.org/display/JENKINS/Extend+Jenkins) wiki page for details.

[Mojo documentation](http://jenkinsci.github.io/maven-hpi-plugin/)

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/maven-hpi-plugin/)

## Changelog

### 1.121 (2016 Dec 16)

* Fixing a problem with plugin dependency resolution affecting users of jitpack.io.

### 1.120 (2016 Sep 26)

* Allowing `hpi:run` to pick up compiled classes & saved resources from core or plugin snapshot dependencies in addition to the plugin under test itself.
* Ensuring `Plugin-Dependencies` appears in a consistent order from build to build.

### 1.119 and earlier

Not recorded.

## For maintainers

```bash
mvn -Prun-its clean install
# Find some plugin using the 2.x parent POM and run:
mvn -f ../some-plugin -Dhpi-plugin.version=1.XXX-SNAPSHOT -Denforcer.fail=false -DskipTests -DjenkinsHome=/tmp/sanity-check-maven-hpi-plugin clean package hpi:run
```

You can also rerun one test:

```bash
mvn -Prun-its mrm:start invoker:run mrm:stop -Dinvoker.test=parent-2x
```

To rerun just the verification script:

```bash
groovy -e "basedir='$(pwd)/target/its/parent-2x'; evaluate new File('src/it/parent-2x/verify.groovy')"
```

Also make sure `project.parent.version` in `src/it/parent-2x/pom.xml` is the latest.

## Updating Jetty
`hpi:run` mojo is a variant of `jetty:run` mojo, and because of the way plugin descriptor is generated, this module copies some code from Jetty Maven plugin, specifically `AbstractJettyMojo.java` and `ConsoleScanner.java`.

To keep upstream tracking easier, prestine copies of these files are copied into `incoming-x.y` branch, then package renamed. This version specific incoming branch is then "theirs" merged into the `incoming` branch, which acts as the upstream tracking branch.

This branch is then merged into `master` via `git merge -X ignore-space-at-eol incoming`. See diff between `incoming` and `master` on these files to see the exact local patches.
