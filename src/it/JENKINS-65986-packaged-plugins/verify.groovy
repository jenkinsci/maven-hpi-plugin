import java.util.jar.Manifest

assert !new File(basedir, 'module2/target/JENKINS-65986-packaged-plugins-module2/WEB-INF/lib/json-path-2.5.0.jar').exists()
assert !new File(basedir, 'module2/target/JENKINS-65986-packaged-plugins-module2/WEB-INF/lib/accessors-smart-1.2.jar').exists()
assert !new File(basedir, 'module2/target/JENKINS-65986-packaged-plugins-module2/WEB-INF/lib/json-smart-2.3.jar').exists()
assert !new File(basedir, 'module2/target/JENKINS-65986-packaged-plugins-module2/WEB-INF/lib/parboiled-core-1.3.1.jar').exists()
assert !new File(basedir, 'module2/target/JENKINS-65986-packaged-plugins-module2/WEB-INF/lib/parboiled-java-1.3.1.jar').exists()
def manifestFile = new File(basedir, 'module2/target/JENKINS-65986-packaged-plugins-module2/META-INF/MANIFEST.MF')
FileInputStream fis = new FileInputStream(manifestFile)
try {
  Manifest mf = new Manifest(fis)
  def attributes = mf.getMainAttributes()
  def pluginDependencies = attributes.getValue("Plugin-Dependencies")
  assert pluginDependencies != null
  assert pluginDependencies.contains("token-macro")
  assert !pluginDependencies.contains("workflow-step-api") // transitive dependency of token-macro, shouldn't appear
} finally {
  fis.close()
}
return true
