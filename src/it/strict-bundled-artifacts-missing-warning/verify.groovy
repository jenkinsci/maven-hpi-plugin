def expectedJars = [
  'jackson-annotations-2.19.1.jar',
  'jackson-core-2.19.1.jar',
  'jackson-databind-2.19.1.jar',
  'metrics-core-5.0.1.jar',
  'metrics-json-5.0.1.jar',
  'strict-bundled-artifacts-missing-warning.jar'
];
def actualJars = new File(basedir, "target/strict-bundled-artifacts-missing-warning/WEB-INF/lib/").list({ dir, file -> file.toString().endsWith(".jar") })
actualJars.sort()
assert actualJars == expectedJars
assert new File(basedir, 'build.log').getText('UTF-8').contains("Expected list of bundled artifacts [metrics-core, metrics-json] did not match actual list of bundled artifacts [jackson-annotations, jackson-core, jackson-databind, metrics-core, metrics-json].")
assert new File(basedir, 'build.log').getText('UTF-8').contains("add `<hpi.bundledArtifacts>jackson-annotations,jackson-core,jackson-databind,metrics-core,metrics-json</hpi.bundledArtifacts>` to `<properties>` in pom.xml")
assert new File(basedir, 'build.log').getText('UTF-8').contains("Enable strict checks by adding `<hpi.strictBundledArtifacts>true</hpi.strictBundledArtifacts>` to pom.xml")

return true;
