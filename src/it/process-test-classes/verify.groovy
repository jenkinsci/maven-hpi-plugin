assert new File(basedir, 'process-test-classes-bar/target/test-classes/test-dependencies/index').getText('UTF-8').contains('process-test-classes-foo')
assert new File(basedir, 'process-test-classes-bar/target/test-classes/test-dependencies/process-test-classes-foo.hpi').exists()

return true
