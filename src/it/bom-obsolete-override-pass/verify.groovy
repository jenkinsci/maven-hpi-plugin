// Build should succeed - newer version override is allowed
def buildLog = new File(basedir, 'build.log')
assert buildLog.exists()
assert buildLog.text.contains('[RequireNonObsoleteDependencyManagement] No obsolete overrides found')
return true
