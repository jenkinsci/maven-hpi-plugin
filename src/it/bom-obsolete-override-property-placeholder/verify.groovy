// Build should pass - jenkins.version uses ${jenkins.baseline} placeholder (recommended pattern)
// Rule should resolve ${jenkins.baseline}.3 to 2.479.3 before comparison
def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('org.jenkinsci.maven.plugins.hpi.enforcer.BanObsoleteDependencyOverrides passed')
// Should NOT report jenkins.version as obsolete
assert !log.contains('Found obsolete property overrides')
return true
