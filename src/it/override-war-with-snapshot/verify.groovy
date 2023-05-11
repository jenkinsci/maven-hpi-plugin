import java.io.File
import java.util.jar.JarFile

assert new File(basedir, 'war-with-plugin/target/override-war-with-snapshot.war').exists()

jf = new JarFile(new File(basedir, 'war-with-plugin/target/override-war-with-snapshot.war'));

assert jf.getEntry('WEB-INF/plugins/override-war-with-snapshot.hpi') != null

return true;
