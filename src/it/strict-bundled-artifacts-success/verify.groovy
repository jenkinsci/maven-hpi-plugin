def expectedJars = [
  'metrics-core-5.0.1.jar',
  'metrics-json-5.0.1.jar',
  'strict-bundled-artifacts-success.jar'
];
def actualJars = new File(basedir, "target/strict-bundled-artifacts-success/WEB-INF/lib/").list({ dir, file -> file.toString().endsWith(".jar") })
actualJars.sort()
assert actualJars == expectedJars
return true;
