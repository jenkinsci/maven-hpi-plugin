def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('[RequireNonObsoleteDependencyManagement] Skipping rule')
return true
