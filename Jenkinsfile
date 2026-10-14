

pipeline {
    // Run on any available Jenkins machine
    agent any

    stages {

        /* Jenkins checks out the branch automatically before the first
         stage, so no explicit checkout stage is needed. */

        stage('Spring Boot tests') {
            environment {
                //  the same test foind in scripts/test.sh uses. Not a real secret.
                JWT_SECRET = 'LEAP_TEST_ONLY_JWT_SECRET_2026_DO_NOT_USE_OUTSIDE_AUTOMATED_TESTS_12345678901234567890'
            }
            steps {
                sh '''
                    # Throwaway PostgreSQL just for this run, on a random free host port
                    docker run -d --rm \
                        -e POSTGRES_DB=leaping_test_db \
                        -e POSTGRES_USER=leap_test \
                        -e POSTGRES_PASSWORD=leap_test_password \
                        -P postgres:16 > .ci-db-id
                    DB=$(cat .ci-db-id)

                    # Wait up to 30 seconds for the database to accept connections
                    for i in $(seq 1 30); do
                        docker exec "$DB" pg_isready -U leap_test -d leaping_test_db && break
                        sleep 1
                    done
                    PORT=$(docker port "$DB" 5432/tcp | head -n 1 | sed 's/.*://')

                    # Point the tests at the throwaway database
                    export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${PORT}/leaping_test_db"
                    export SPRING_DATASOURCE_USERNAME=leap_test
                    export SPRING_DATASOURCE_PASSWORD=leap_test_password
                    export SPRING_FLYWAY_URL="$SPRING_DATASOURCE_URL"
                    export SPRING_FLYWAY_USER=leap_test
                    export SPRING_FLYWAY_PASSWORD=leap_test_password

                    cd backend/portfolio-app
                    bash ./mvnw -B clean test
                '''
            }
            post {
                always {
                    // Remove the throwaway database and publish the test report,
                    // whether the tests passed or failed
                    sh 'docker rm -f "$(cat .ci-db-id)" || true'
                    junit 'backend/portfolio-app/target/surefire-reports/*.xml'
                }
            }
        }

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
