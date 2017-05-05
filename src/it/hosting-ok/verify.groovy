def buildlog = new File(basedir, 'build.log').text

assert ( buildlog =~ /REQUIRED =>/ ).count == 0
assert ( buildlog =~ /WARNING =>/ ).count == 0
assert ( buildlog =~ /INFO =>/ ).count == 0

return true;
