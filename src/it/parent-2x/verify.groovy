import java.util.jar.*

assert new File(basedir, 'target/parent-2x.hpi').exists()
assert new File(basedir, 'target/parent-2x.jar').exists()

File installed = new File(basedir, '../../local-repo/org/jenkins-ci/tools/hpi/its/parent-2x/1.0-SNAPSHOT/')
assert installed.directory

def checkArtifact(File installed, String artifact, List<String> expectedEntries, List<String> unexpectedEntries, Map<String,String> expectedManifestEntries) {
    File f = new File(installed, artifact)
    assert f.file
    JarFile j = new JarFile(f)
    try {
        expectedEntries.each {entry -> assert j.getEntry(entry) != null : "no ${entry} in ${f}"}
        unexpectedEntries.each {entry -> assert j.getEntry(entry) == null : "unwanted ${entry} in ${f}"}
        Attributes attr = j.manifest.mainAttributes
        expectedManifestEntries.each {header, value -> assert attr.getValue(header) == value}
    } finally {
        j.close()
    }
}

checkArtifact(installed, 'parent-2x-1.0-SNAPSHOT.hpi',
    // TODO could also check src/main/webapp/images/32x32/foo.png → images/32x32/foo.png
    ['WEB-INF/lib/parent-2x.jar'],
    // TODO still some problems with unwanted transitive JAR dependencies creeping in, e.g. WEB-INF/lib/jboss-marshalling-1.4.9.Final.jar in workflow-multibranch.hpi, or all kinds of junk in parameterized-trigger.hpi
    ['test/SampleRootAction.class', 'WEB-INF/lib/symbol-annotation-1.5.jar'],
    ['Short-Name': 'parent-2x', 'Group-Id': 'org.jenkins-ci.tools.hpi.its', 'Jenkins-Version': '2.19.2' /* Plugin-Version unpredictable for a snapshot */, 'Plugin-Dependencies': 'structs:1.5'])

checkArtifact(installed, 'parent-2x-1.0-SNAPSHOT.jar',
    ['META-INF/annotations/hudson.Extension', 'test/SampleRootAction.class', 'index.jelly'],
    [],
    ['Short-Name': 'parent-2x'])

checkArtifact(installed, 'parent-2x-1.0-SNAPSHOT-javadoc.jar',
    ['test/SampleRootAction.html'],
    [],
    [:])

checkArtifact(installed, 'parent-2x-1.0-SNAPSHOT-sources.jar',
    ['test/SampleRootAction.java', 'index.jelly'],
    [],
    [:])

checkArtifact(installed, 'parent-2x-1.0-SNAPSHOT-tests.jar',
    ['test/SampleRootActionTest.class', 'test/expected.txt'],
    ['the.hpl', 'InjectedTest.class', 'test-dependencies/structs.hpi'],
    [:])

checkArtifact(installed, 'parent-2x-1.0-SNAPSHOT-test-sources.jar',
    ['test/SampleRootActionTest.java', 'test/expected.txt'],
    ['InjectedTest.java'],
    [:])

// TODO try mvn hpi:run (how do we find the right version of mvn to run?) and check that we can access http://localhost:8080/jenkins/sample/

// TODO run a copy of jenkins.war with the installed *.hpi predeployed and do a similar check

return true

// TODO make similar test for plugin with no-test-jar=true (the default)
// TODO also test building with: JITPACK=true mvn -DskipTests clean install
