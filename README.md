# Maven HPI Plugin

Maven plugin to build Jenkins plugins.
See the [developer guide](https://jenkins.io/doc/developer/plugin-development/) for details.

[Mojo documentation](https://jenkinsci.github.io/maven-hpi-plugin/)

## Changelog

### Newer versions

See [GitHub Releases](https://github.com/jenkinsci/maven-hpi-plugin/releases)

### Older versions

See [archive](https://github.com/jenkinsci/maven-hpi-plugin/tree/24b27178f4dbdb9eeb395f35fc94774d514c980a#35-2019-03-28).

## For maintainers

```bash
mvn -Prun-its clean install
# Find some plugin using the 2.x parent POM and run:
mvn -f ../some-plugin -Dhpi-plugin.version=2.XXX-SNAPSHOT -DskipTests -DjenkinsHome=/tmp/sanity-check-maven-hpi-plugin clean package hpi:run
```

You can also rerun one test:

```bash
mvn -Prun-its mrm:start invoker:run mrm:stop -Dinvoker.test=parent-4x
```

To rerun just the verification script:

```bash
groovy -e "basedir='$(pwd)/target/its/parent-4x'; evaluate new File('src/it/parent-4x/verify.groovy')"
```

Also make sure `project.parent.version` is the latest in every integration test except `src/it/parent-4-40/pom.xml`.
