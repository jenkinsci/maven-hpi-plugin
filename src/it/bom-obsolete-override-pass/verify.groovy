// Build should succeed - newer version override is allowed
def buildLog = new File(basedir, 'build.log')
assert buildLog.exists()
assert buildLog.text.contains('org.jenkinsci.maven.plugins.hpi.enforcer.RequireNonObsoleteDependencyManagement passed')
return true
