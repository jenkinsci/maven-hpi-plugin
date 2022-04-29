assert new File(basedir, 'build.log').getText('UTF-8').contains('Cannot override dependencies when doing a release')

return true
