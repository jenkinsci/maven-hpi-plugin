def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('org.jenkinsci.maven.plugins.hpi.enforcer.BanObsoleteDependencyOverrides passed')
return true
