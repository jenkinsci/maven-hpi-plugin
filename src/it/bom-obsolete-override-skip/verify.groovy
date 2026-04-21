def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('Skipping RequireNonObsoleteDependencyManagement rule')
return true
