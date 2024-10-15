assert new File(basedir, 'resolve-test-dependencies-bar/target/test-classes/test-dependencies/index').getText('UTF-8').contains('resolve-test-dependencies-foo')
assert new File(basedir, 'resolve-test-dependencies-bar/target/test-classes/test-dependencies/resolve-test-dependencies-foo.hpi').exists()

return true
