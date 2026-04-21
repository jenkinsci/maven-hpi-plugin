// Build should succeed - newer version override is allowed
assert new File(basedir, 'build.log').exists()
return true
