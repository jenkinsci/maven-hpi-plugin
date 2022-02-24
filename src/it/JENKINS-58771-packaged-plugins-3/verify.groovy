assert !new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib/jsr305-3.0.2.jar').exists()
assert new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib').listFiles().length == 1
assert new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib/JENKINS-58771-packaged-plugins.jar').exists()

return true
