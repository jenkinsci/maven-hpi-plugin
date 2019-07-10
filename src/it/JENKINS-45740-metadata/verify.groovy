import java.util.jar.*

assert new File(basedir, 'target/JENKINS-45740-metadata.hpi').exists()

File installed = new File(basedir, '../../local-repo/org/jenkins-ci/tools/hpi/its/JENKINS-45740-metadata/1.0-SNAPSHOT/')
assert installed.directory

def checkArtifact(File installed, String artifact, Map<String,String> expectedManifestEntries) {
    File f = new File(installed, artifact)
    assert f.file
    JarFile j = new JarFile(f)
    try {
        Attributes attr = j.manifest.mainAttributes
        expectedManifestEntries.each {header, value -> assert attr.getValue(header) == value}
    } finally {
        j.close()
    }
}

checkArtifact(
    installed,
    'JENKINS-45740-metadata-1.0-SNAPSHOT.hpi',
    [
      'Plugin-ChangelogUrl': 'http://plugins.jenkins.io/JENKINS-45740-metadata/changelog',
      'Plugin-LogoUrl': 'http://plugins.jenkins.io/JENKINS-45740-metadata/logo.png',
      'Plugin-ScmUrl': 'http://github.com/jenkinsci/JENKINS-45740-metadata-plugin',
      'Plugin-License-Name-1': 'MIT License',
      'Plugin-License-Url-1': 'https://opensource.org/licenses/MIT',
      'Plugin-License-Distribution-1': 'repo',
      'Plugin-License-Comments-1': null,
      'Plugin-License-Name-2': 'MY License',
      'Plugin-License-Url-2': 'http://mylicense.txt',
      'Plugin-License-Distribution-2': null,
      'Plugin-License-Comments-2': "Hello, world!"
    ])

return true
