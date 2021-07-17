assert new File(basedir, 'target/JENKINS-64408-packaged-webresources/WEB-INF/lib').listFiles().length == 1
assert new File(basedir, 'target/JENKINS-64408-packaged-webresources/WEB-INF/lib/JENKINS-64408-packaged-webresources.jar').exists()
assert new File(basedir, 'target/JENKINS-64408-packaged-webresources/standard-resource.txt').exists()
assert !new File(basedir, 'target/JENKINS-64408-packaged-webresources/non-standard-resource-in-subdir.txt').exists()
assert new File(basedir, 'target/JENKINS-64408-packaged-webresources/dir-within-hpi').exists()
assert new File(basedir, 'target/JENKINS-64408-packaged-webresources/dir-within-hpi').listFiles().length == 1
assert new File(basedir, 'target/JENKINS-64408-packaged-webresources/dir-within-hpi/non-standard-resource-in-subdir.txt').exists()

return true
