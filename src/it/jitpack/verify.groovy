import java.util.jar.*

File hpi = new File(basedir, '../../local-repo/org/jenkins-ci/tools/hpi/its/jitpack/1.0-SNAPSHOT/jitpack-1.0-SNAPSHOT.hpi')
assert hpi.file
JarFile j = new JarFile(hpi)
try {
    Attributes attr = j.manifest.mainAttributes
    assert attr.getValue('Plugin-Dependencies') == 'scm-api:2.0.1-SNAPSHOT'
} finally {
    j.close()
}

return true
