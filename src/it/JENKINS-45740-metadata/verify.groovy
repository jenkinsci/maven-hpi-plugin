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
      'Plugin-ChangelogUrl': 'https://github.com/jenkinsci/configuration-as-code-plugin/releases',
      'Plugin-LogoUrl': 'https://raw.githubusercontent.com/jenkinsci/configuration-as-code-plugin/master/plugin/src/main/webapp/img/logo-head.svg',
      'Plugin-ScmUrl': 'https://github.com/jenkinsci/JENKINS-45740-metadata-plugin',
      'Plugin-License-Name': 'MIT License',
      'Plugin-License-Url': 'https://opensource.org/licenses/MIT',
      'Plugin-License-Name-2': 'MY License',
      'Plugin-License-Url-2': 'https://mylicense.txt',
    ])

return true
