node('docker') {
  checkout scm
  docker.image('maven:3.3.9-jdk-8').inside {
    sh 'mvn -B -Prun-its clean install'
  }
  /* Useful for debugging failures, but currently too big (~25Mb):
  archiveArtifacts 'target/its/*/build.log'
  */
}
