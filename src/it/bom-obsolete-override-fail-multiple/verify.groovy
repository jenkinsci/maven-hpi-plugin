def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('Found obsolete dependency version overrides')
// Verify all three dependencies are reported in the same failure message
assert log.contains('org.jenkins-ci.plugins:credentials')
assert log.contains('org.jenkins-ci.plugins.workflow:workflow-api')
assert log.contains('org.jenkins-ci.plugins.workflow:workflow-support')
assert log.contains('bom-2.479.x')
return true
