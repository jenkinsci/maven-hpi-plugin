assert ! new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib/joda-time-2.9.5.jar').exists()
assert ! new File(basedir, 'target/JENKINS-58771-packaged-plugins/WEB-INF/lib/multiline-secrets-ui-1.0.jar').exists()

return true
