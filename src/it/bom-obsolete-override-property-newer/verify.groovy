// Build should pass - property override is newer than parent (valid override)
def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('org.jenkinsci.maven.plugins.hpi.enforcer.RequireNonObsoleteDependencyManagement passed')
// Should NOT contain property violations
assert !log.contains('Found obsolete property overrides')
return true
