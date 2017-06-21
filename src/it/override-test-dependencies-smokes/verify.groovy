def log = new File(basedir, 'build.log').text
assert log.contains('overrideVersions=[whatever, else] useUpperBounds=true')

true