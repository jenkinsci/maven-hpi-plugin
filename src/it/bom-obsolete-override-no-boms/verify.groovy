def log = new File(basedir, 'build.log').getText('UTF-8')
// Build should pass - no BOMs means no BOM-related violations to check
// Property check still runs but finds no violations
assert log.contains('org.jenkinsci.maven.plugins.hpi.enforcer.BanObsoleteDependencyOverrides passed')
return true
