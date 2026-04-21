def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('Found obsolete dependency version overrides')
assert log.contains('older than or equal to')
assert log.contains('org.jenkins-ci.plugins:credentials')
assert log.contains('1415.v831096eb_5534')
return true
