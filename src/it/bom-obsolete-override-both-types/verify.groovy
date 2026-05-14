// Build should fail due to BOTH obsolete dependency and property overrides
def log = new File(basedir, 'build.log').getText('UTF-8')
// Check for dependency violations
assert log.contains('Found obsolete dependency version overrides')
assert log.contains('org.jenkins-ci.plugins:credentials: declared 1.200 < 1415.v831096eb_5534 from imported BOM bom-2.479.x')
// Check for property violations
assert log.contains('Found obsolete property overrides')
assert log.contains('jenkins-test-harness.version')
assert log.contains('declared 2535.va_83a_c5b_19a_89 < 2537.v48fd29a_7070d from parent POM')
return true
