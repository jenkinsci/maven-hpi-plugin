// Build should fail due to obsolete override in parent POM
// The enforcer runs on the parent module before processing the child
def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('Found obsolete dependency version overrides')
assert log.contains('org.jenkins-ci.plugins:credentials')
assert log.contains('bom-2.479.x')
// Verify the failure happened during parent processing, not child
assert log.contains('Building Test Plugin - BOM Obsolete Override Multimodule Parent')
return true
