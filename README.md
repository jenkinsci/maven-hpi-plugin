Maven plugin to build Jenkins plugins.
See the [developer guide](https://jenkins.io/doc/developer/plugin-development/) for details.

[Mojo documentation](http://jenkinsci.github.io/maven-hpi-plugin/)

## Changelog

### 3.0 (2018-11-30)

* [JENKINS-20679](https://issues.jenkins-ci.org/browse/JENKINS-20679) - 
Inject `Minimum-Java-Version` into the manifest.
  * It is set by a new mandatory `minimumJavaVersion` parameter in `hpi:hpi`, `hpi:jar` and `hpi:hpl`
  * Format: `java.specification.version` according to [Java JEP-223](https://openjdk.java.net/jeps/223). 
    Examples: `1.6`, `1.7`, `1.8`, `6`, `7`, `8`, `9`, `11`, ...
* Internal: Move manifest-related parameters and logic to `AbstractJenkinsManifestMojo`

### 2.7 (2018-10-30)

* Delete `work/plugins/*.jpl` where the current `test`-scoped dependency is not in fact a snapshot.
* Use a more specific temp dir for Jetty.

### 2.6 (2018 Jun 01)

* Bugs in the dependency copy of `mvn hpi:run` could lead to anomalies such as `work/plugins/null.jpi`.

### 2.5 (2018 May 11)

* Option to override a snapshot plugin version with a more informative string.

### 2.3 (2018 Apr 19)

* Using a newer standard `VersionNumber` that precisely matches the behavior of the Jenkins plugin manager.

### 2.2 (2018 Jan 30)

* Fix `mvn clean hpi:run` and some similar special goal sequences.

### 2.1 (2017 Sep 26)

* Jenkins plugin archetypes are no longer bundled with this Maven plugin. Instead use the [new project](https://github.com/jenkinsci/archetypes/blob/master/README.md#introduction).
* Making `-DwebAppFile=…` work.
* Fixing unchecked/rawtypes warnings in `InjectedTest`.
* No more special handling of artifacts with `-ea` in the version.

### 2.0 (2017 May 25)

* Updated integrated Jetty server to 9.x. This means that JDK 8 is now required at build time. (Plugins may continue to target older Java baselines using the `java.level` property in the 2.x parent POM.)
* [JENKINS-24064](https://issues.jenkins-ci.org/browse/JENKINS-24064) Added `executable-war` artifact type, permitting Jenkins to stop deploying the wasteful `jenkins-war-*-war-for-test.jar` artifact, which was identical to `jenkins-war-*.war`.

### 1.122 (2017 Apr 12)

* Fixed HTML escaping for Javadoc created for taglibs so it can be processed by JDK 8.
* Logging the current artifact for `InjectedTest`.
* More fixes to mojos that assumed that plugin artifacts used a short name identical to the `artifactId`.
* Minor archetype updates.

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
mvn -f ../some-plugin -Dhpi-plugin.version=2.XXX-SNAPSHOT -DskipTests -DjenkinsHome=/tmp/sanity-check-maven-hpi-plugin clean package hpi:run
```

You can also rerun one test:

```bash
mvn -Prun-its mrm:start invoker:run mrm:stop -Dinvoker.test=parent-3x
```

To rerun just the verification script:

```bash
groovy -e "basedir='$(pwd)/target/its/parent-3x'; evaluate new File('src/it/parent-3x/verify.groovy')"
```

Also make sure `project.parent.version` in `src/it/parent-3x/pom.xml` is the latest.

## Updating Jetty
`hpi:run` mojo is a variant of `jetty:run` mojo, and because of the way plugin descriptor is generated, this module copies some code from Jetty Maven plugin, specifically `AbstractJettyMojo.java` and `ConsoleScanner.java`.

To keep upstream tracking easier, pristine copies of these files are copied into `incoming-x.y` branch, then package renamed. This version specific incoming branch is then "theirs" merged into the `incoming` branch, which acts as the upstream tracking branch.

This branch is then merged into `master` via `git merge -X ignore-space-at-eol incoming`. See diff between `incoming` and `master` on these files to see the exact local patches.
