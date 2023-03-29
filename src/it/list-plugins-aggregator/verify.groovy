List lines = new File(basedir, 'plugins.txt').readLines('UTF-8')

// invoker plugin is brain dead and sets incorrect path
// like c:\foo\bar when it should be C:\foo\bar
// so we need to canonicalize the basedir
// https://issues.apache.org/jira/browse/MINVOKER-334
def projectRoot = basedir.getCanonicalPath() + File.separator

assert lines.size() == 2
assert lines.get(0).trim() == "whizzbang\t1.0-SNAPSHOT\t${projectRoot}a-plugin"
assert lines.get(1).trim() == "list-plugins-plugin1\t1.0-SNAPSHOT\t${projectRoot}first-module"
return true
