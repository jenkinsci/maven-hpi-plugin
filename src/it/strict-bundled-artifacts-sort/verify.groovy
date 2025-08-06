assert new File(basedir, 'build.log').getText('UTF-8').contains("Expected list of bundled artifacts [metrics-json, metrics-core] did not match actual list of bundled artifacts [metrics-core, metrics-json].")
assert new File(basedir, 'build.log').getText('UTF-8').contains("add `<hpi.bundledArtifacts>metrics-core,metrics-json</hpi.bundledArtifacts>` to `<properties>` in pom.xml")

return true;
