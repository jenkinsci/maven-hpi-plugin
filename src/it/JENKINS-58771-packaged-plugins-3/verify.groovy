assert !new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib/jsr305-3.0.2.jar').exists()
def libDir = new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib')
def libFiles = libDir.listFiles()
assert libFiles != null && libFiles.length == 1
assert new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib/JENKINS-58771-packaged-plugins.jar').exists()

return true
