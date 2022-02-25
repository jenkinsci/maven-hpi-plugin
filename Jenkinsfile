properties([buildDiscarder(logRotator(numToKeepStr: '20'))])
node('maven') {
    checkout scm
    timeout(time: 1, unit: 'HOURS') {
        ansiColor('xterm') {
            withEnv(['MAVEN_OPTS=-Djansi.force=true']) {
                sh 'mvn -B -Dstyle.color=always -ntp -Prun-its -Dmaven.test.failure.ignore clean install site'
                junit 'target/invoker-reports/TEST-*.xml'
            }
        }
    }
}
