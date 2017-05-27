properties([buildDiscarder(logRotator(numToKeepStr: '20'))])
node('docker') {
  checkout scm
  docker.image('maven:3.3.9-jdk-8').inside {
    try {
      sh 'mvn -B -Prun-its clean install site'
    } catch (e) {
      // Too big (~25Mb) to archive for successful builds:
      archiveArtifacts artifacts: 'target/its/*/build.log', allowEmptyArchive: true
      throw e
    }
  }
}
