import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

assert new File(basedir, 'plugin1/target/plugin1.hpi').exists()
File p1 = new File(basedir, 'plugin1/target/plugin1.hpi')

assert p1.exists()
try (JarFile j1 = new JarFile(p1)) {
    Manifest mf = j1.getManifest()
    Attributes attributes = mf.getMainAttributes()
    // there may be other attribute like 'Created-By' that we do not care about we are not checking everything
    assert attributes.getValue('Manifest-Version').equals('1.0') // if this changes then Jenkins may not be able to parse it.

    assert attributes.getValue('Hudson-Version').equals('2.361.4')
    assert attributes.getValue('Jenkins-Version').equals('2.361.4')

    assert attributes.getValue('Group-Id').equals('org.jenkins-ci.tools.hpi.its.git-metadata')
    assert attributes.getValue('Artifact-Id').equals('plugin1')

    assert attributes.getValue('Short-Name').equals('plugin1') // artifactId
    assert attributes.getValue('Implementation-Version').equals('1.0-SNAPSHOT') // version

    // using project.name
    assert attributes.getValue('Implementation-Title').equals('My First Plugin')
    assert attributes.getValue('Specification-Title').equals('My First Plugin')
    assert attributes.getValue('Long-Name').equals('My First Plugin')
    
    assert attributes.getValue('Plugin-Developers').isEmpty()
    assert attributes.getValue('Plugin-License-Name').equals('MIT License')
    assert attributes.getValue('Plugin-License-Url').equals('https://opensource.org/licenses/MIT')
    assert attributes.getValue('Plugin-ScmUrl').equals('https://github.com/jenkinsci/maven-hpi-plugin')
    assert attributes.getValue('Plugin-Version').startsWith('1.0-SNAPSHOT')
    assert attributes.getValue('Url').equals('https://plugins.jenkins.io/plugin1/')
    assert attributes.getValue('Plugin-ScmConnection').equals('scm:git:https://github.com/jenkinsci/maven-hpi-plugin.git')
    assert attributes.getValue('Plugin-GitHash').length() == 40
    assert attributes.getValue('Plugin-Module').equals('plugin1') // invoker copies the file so this is where it is actually built.
}


assert new File(basedir, 'plugin2/target/multimodule-it-plugin2.hpi').exists();
File p2 = new File(basedir, 'plugin2/target/multimodule-it-plugin2.hpi')

try (JarFile j2 = new JarFile(p2)) {
    Manifest mf = j2.getManifest()
    Attributes attributes = mf.getMainAttributes()
    assert attributes.getValue('Manifest-Version').equals('1.0') // if this changes then Jenkins may not be able to parse it.

    assert attributes.getValue('Hudson-Version').equals('2.361.4')
    assert attributes.getValue('Jenkins-Version').equals('2.361.4')

    assert attributes.getValue('Group-Id').equals('org.jenkins-ci.tools.hpi.its.git-metadata')
    assert attributes.getValue('Artifact-Id').equals('multimodule-it-plugin2')
    assert attributes.getValue('Short-Name').equals('multimodule-it-plugin2') // artifactId
    assert attributes.getValue('Implementation-Version').equals('1.0-SNAPSHOT') // version

    // project.name not set so should fallback to project.artifactId
    assert attributes.getValue('Implementation-Title').equals('multimodule-it-plugin2')
    assert attributes.getValue('Specification-Title').equals('multimodule-it-plugin2')
    assert attributes.getValue('Long-Name').equals('multimodule-it-plugin2')
    
    assert attributes.getValue('Plugin-Developers').equals('Robert McBobface:bob:bob@example.com,:jane:jane@example.com')
    assert attributes.getValue('Plugin-License-Name').equals('MIT License')
    assert attributes.getValue('Plugin-License-Url').equals('https://opensource.org/licenses/MIT')
    assert attributes.getValue('Plugin-ScmUrl').equals('https://github.com/jenkinsci/maven-hpi-plugin')
    assert attributes.getValue('Plugin-Version').startsWith('1.0-SNAPSHOT')
    assert attributes.getValue('Url').equals('https://plugins.jenkins.io/multimodule-it-plugin2/')
    assert attributes.getValue('Plugin-ScmConnection').equals('scm:git:https://github.com/jenkinsci/maven-hpi-plugin.git')
    assert attributes.getValue('Plugin-GitHash').length() == 40
    assert attributes.getValue('Plugin-Module').equals('plugin2') // invoker copies the file so this is where it is actualy built.

}

return true;