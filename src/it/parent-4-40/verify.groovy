import java.util.jar.*

assert new File(basedir, 'target/parent-4-40.hpi').exists()
assert new File(basedir, 'target/parent-4-40.jar').exists()

File installed = new File(basedir, '../../local-repo/org/jenkins-ci/tools/hpi/its/parent-4-40/1.0-SNAPSHOT/')
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

checkArtifact(installed, 'parent-4-40-1.0-SNAPSHOT.hpi',
    // TODO could also check src/main/webapp/images/32x32/foo.png → images/32x32/foo.png
    ['WEB-INF/lib/parent-4-40.jar'],
    // TODO still some problems with unwanted transitive JAR dependencies creeping in, e.g. WEB-INF/lib/jboss-marshalling-1.4.9.Final.jar in workflow-multibranch.hpi, or all kinds of junk in parameterized-trigger.hpi
    ['test/SampleRootAction.class', 'WEB-INF/lib/symbol-annotation-1.5.jar'],
    ['Short-Name': 'parent-4-40', 'Group-Id': 'org.jenkins-ci.tools.hpi.its', 'Jenkins-Version': '2.361.4' /* Plugin-Version unpredictable for a snapshot */, 'Plugin-Dependencies': 'structs:324.va_f5d6774f3a_d'])

checkArtifact(installed, 'parent-4-40-1.0-SNAPSHOT.jar',
    ['META-INF/annotations/hudson.Extension', 'test/SampleRootAction.class', 'index.jelly'],
    [],
    ['Short-Name': 'parent-4-40'])

checkArtifact(installed, 'parent-4-40-1.0-SNAPSHOT-javadoc.jar',
    ['test/SampleRootAction.html'],
    [],
    [:])

checkArtifact(installed, 'parent-4-40-1.0-SNAPSHOT-sources.jar',
    ['test/SampleRootAction.java', 'index.jelly'],
    [],
    [:])

checkArtifact(installed, 'parent-4-40-1.0-SNAPSHOT-tests.jar',
    ['test/SampleRootActionTest.class', 'test/expected.txt'],
    ['the.hpl', 'InjectedTest.class', 'test-dependencies/structs.hpi'],
    [:])

checkArtifact(installed, 'parent-4-40-1.0-SNAPSHOT-test-sources.jar',
    ['test/SampleRootActionTest.java', 'test/expected.txt'],
    [],
    [:])

// TODO check that we can access http://localhost:8080/jenkins/sample/ during hpi:run
// (tricky since this script is called only once mvn is done)
def text = new File(basedir, 'build.log').text
assert text.contains('Jenkins is fully up and running') && text.contains('INFO\thudson.lifecycle.Lifecycle#onStatusUpdate: Jenkins stopped')
assert new File(basedir, 'work/plugins/parent-4-40.hpl').file
assert new File(basedir, 'work/plugins/structs.jpi').file

// TODO run a copy of jenkins.war with the installed *.hpi predeployed and do a similar check

return true

// TODO make similar test for plugin with no-test-jar=true (the default)
// TODO also test building with: JITPACK=true mvn -DskipTests clean install
