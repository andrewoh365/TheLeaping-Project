

pipeline {
    // Run on any available Jenkins machine
    agent any

    stages {

        /* Jenkins checks out the branch automatically before the first
         stage, so no explicit checkout stage is needed. */

        stage('Build Spring Boot image') {
            steps {
                // Builds from backend/portfolio-app/Dockerfile.
                // Tagged with the Jenkins build number so each run is identifiable.
                sh 'docker build -t leap-portfolio-app:${BUILD_NUMBER} backend/portfolio-app'
            }
        }
    }

    // Runs after all stages, whatever the result
    post {
        success {
            echo "Build ${BUILD_NUMBER} passed."
        }
        failure {
            echo "Build ${BUILD_NUMBER} failed - check the console output."
        }
    }
}
