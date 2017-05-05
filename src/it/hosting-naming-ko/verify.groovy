def buildlog = new File(basedir, 'build.log').text

assert ( buildlog =~ /REQUIRED =>/ ).count == 2
assert ( buildlog =~ /INFO =>/ ).count == 1

return true;
