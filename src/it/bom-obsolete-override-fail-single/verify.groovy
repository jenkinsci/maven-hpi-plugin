// Build should fail due to obsolete override
def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('Found obsolete dependency version overrides')
assert log.contains('org.jenkins-ci.plugins:credentials')
assert log.contains('bom-2.479.x')
return true
