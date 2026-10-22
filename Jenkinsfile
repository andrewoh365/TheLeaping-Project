

pipeline {
    // Run on any available Jenkins machine
    agent any

    environment {
        // Unique image tag per branch and build, e.g. feature-jenkins-3.
        // Characters Docker does not allow in a tag (like "/") become "-".
        IMAGE_TAG = "${env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9_.-]', '-')}-${env.BUILD_NUMBER}"
    }

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

        stage('Build analytics image') {
            steps {
                // Builds from backend/analytics-service/Dockerfile.
                sh 'docker build -t leap-analytics:$IMAGE_TAG backend/analytics-service'
            }
        }

        stage('Python tests') {
            steps {
                // Runs pytest inside the image just built. The tests are not part
                // of the image, so they are mounted in read-only from the workspace.
                sh '''
                    docker run --rm \
                        -v "$PWD/backend/analytics-service/tests:/app/tests:ro" \
                        -v "$PWD/backend/analytics-service/pytest.ini:/app/pytest.ini:ro" \
                        leap-analytics:$IMAGE_TAG \
                        pytest -q
                '''
            }
        }

        stage('Build Spring Boot image') {
            steps {
                // Builds from backend/portfolio-app/Dockerfile.
                sh 'docker build -t leap-portfolio-app:$IMAGE_TAG backend/portfolio-app'
            }
        }

        stage('Spring Boot health check') {
            environment {
                // Same test-only secret as the Spring Boot tests stage. Not a real secret.
                JWT_SECRET = 'LEAP_TEST_ONLY_JWT_SECRET_2026_DO_NOT_USE_OUTSIDE_AUTOMATED_TESTS_12345678901234567890'
            }
            steps {
                // Starts the image just built against a throwaway PostgreSQL and waits for
                // /actuator/health to report UP, the same check ECS will run. Catches startup
                // problems the unit tests miss, like a deadlock during the price backfill.
                sh '''
                    NET="leap-ci-$IMAGE_TAG"
                    docker network create "$NET"
                    docker run -d --name "$NET-db" --network "$NET" \
                        -e POSTGRES_DB=leaping_db \
                        -e POSTGRES_USER=leap_ci \
                        -e POSTGRES_PASSWORD=leap_ci_password \
                        postgres:16

                    # Wait up to 30 seconds for the database to accept connections
                    for i in $(seq 1 30); do
                        docker exec "$NET-db" pg_isready -U leap_ci -d leaping_db && break
                        sleep 1
                    done

                    # Price updates run every second here (normally every 30) so one always
                    # overlaps the startup backfill. A lock-order bug between the two then
                    # fails this check every time instead of only when the timing lines up.
                    docker run -d --name "$NET-app" --network "$NET" \
                        -e SPRING_DATASOURCE_URL="jdbc:postgresql://$NET-db:5432/leaping_db" \
                        -e DB_USERNAME=leap_ci \
                        -e DB_PASSWORD=leap_ci_password \
                        -e JWT_SECRET \
                        -e MARKET_PRICING_REFRESHMS=1000 \
                        leap-portfolio-app:$IMAGE_TAG

                    # Up to 3 minutes, the startup grace period planned for ECS
                    for i in $(seq 1 36); do
                        if [ "$(docker inspect -f '{{.State.Running}}' "$NET-app")" != "true" ]; then
                            echo "Spring stopped during startup. Last log lines:"
                            docker logs --tail 80 "$NET-app"
                            exit 1
                        fi
                        if docker exec "$NET-app" wget -qO- http://localhost:8080/actuator/health; then
                            echo
                            echo "Spring reported UP after about $((i * 5)) seconds"
                            exit 0
                        fi
                        sleep 5
                    done

                    echo "Spring did not report UP within 3 minutes. Last log lines:"
                    docker logs --tail 80 "$NET-app"
                    exit 1
                '''
            }
            post {
                always {
                    // Remove the throwaway containers and network, whether the check passed or failed
                    sh '''
                        NET="leap-ci-$IMAGE_TAG"
                        docker rm -f "$NET-app" "$NET-db" || true
                        docker network rm "$NET" || true
                    '''
                }
            }
        }

        stage('Build frontend image') {
            steps {
                // Builds from frontend/Dockerfile. This runs the Angular production
                // build, so it also fails if a size budget in angular.json is exceeded.
                sh 'docker build -t leap-frontend:$IMAGE_TAG frontend'
            }
        }
    }

    // Runs after all stages, whatever the result
    post {
        always {
            // The images were only built to prove they build. Remove this run's
            // tags so they do not pile up on the Jenkins machine.
            sh 'docker rmi leap-analytics:$IMAGE_TAG leap-portfolio-app:$IMAGE_TAG leap-frontend:$IMAGE_TAG || true'
        }
        success {
            echo "Build ${BUILD_NUMBER} passed."
        }
        failure {
            echo "Build ${BUILD_NUMBER} failed - check the console output."
        }
    }
}
