def log = new File(basedir, 'build.log').getText('UTF-8')
// Build should pass - no BOMs means no BOM-related violations to check
// Property check still runs but finds no violations
assert log.contains('[RequireNonObsoleteDependencyManagement] No obsolete overrides found')
return true
