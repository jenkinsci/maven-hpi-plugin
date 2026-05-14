// Build should fail due to obsolete property override (equal version - unnecessary override)
def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('Found obsolete property overrides')
assert log.contains('jenkins-test-harness.version')
assert log.contains('declared 2537.v48fd29a_7070d == 2537.v48fd29a_7070d from parent POM')
// Should NOT contain dependency violations
assert !log.contains('Found obsolete dependency version overrides')
return true
