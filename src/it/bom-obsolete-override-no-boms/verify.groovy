def log = new File(basedir, 'build.log').getText('UTF-8')
assert log.contains('No imported BOMs found')
return true
