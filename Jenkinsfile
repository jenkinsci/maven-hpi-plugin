properties([buildDiscarder(logRotator(numToKeepStr: '20'))])
node('maven') {
    checkout scm
    timeout(time: 1, unit: 'HOURS') {
        // TODO Azure mirror
        ansiColor('xterm') {
            withEnv(['MAVEN_OPTS=-Djansi.force=true']) {
                sh 'mvn -B -Dstyle.color=always -ntp -Prun-its clean install site'
            }
        }
    }
}
