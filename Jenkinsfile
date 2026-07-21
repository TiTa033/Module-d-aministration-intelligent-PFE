pipeline {
    agent any

    environment {
        SONAR_HOST_URL = 'http://raas-sonarqube:9000'
        SONAR_TOKEN    = credentials('sonar-token')
        DB_PASSWORD    = credentials('db-password')
        APP_NAME       = 'raas-backend'
        APP_PORT       = '8090'
        DOCKER_NETWORK = 'cicd_raas-cicd'
        DOCKER_IMAGE = 'tita03/raas-backend'
        DO_HOST = '104.248.53.127'
        DO_USER = 'deploy'
        DO_IMAGE = 'tita03/raas-backend'

    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-21'
    }

    stages {

        stage('🔍 Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.GIT_BRANCH}"
            }
        }

        stage('🏗 Build') {
            steps {
                sh 'mvn clean compile -q'
            }
        }

        stage('🧪 Tests') {
            steps {
                sh 'mvn test -q'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('📦 Package') {
            steps {
                sh 'mvn package -DskipTests -q'
            }
        }

        stage('🔎 SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                          -Dsonar.projectKey=raas-backend \
                          -Dsonar.projectName='RaaS Backend' \
                          -Dsonar.host.url=${SONAR_HOST_URL} \
                          -Dsonar.token=${SONAR_TOKEN}
                    """
                }
            }
        }

        stage('✅ Quality Gate') {
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: false
                }
            }
        }

        stage('🐳 Docker Build & Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {

                    sh """
                        echo \$DOCKER_PASS | docker login \
                            -u \$DOCKER_USER \
                            --password-stdin

                        docker build \
                            -t ${DOCKER_IMAGE}:${BUILD_NUMBER} .

                        docker tag \
                            ${DOCKER_IMAGE}:${BUILD_NUMBER} \
                            ${DOCKER_IMAGE}:latest

                        docker push \
                            ${DOCKER_IMAGE}:${BUILD_NUMBER}

                        docker push \
                            ${DOCKER_IMAGE}:latest
                    """
                }
            }
        }

        stage('🚀 Deploy to DigitalOcean') {
            steps {
                sh """
                    ssh -o StrictHostKeyChecking=no ${DO_USER}@${DO_HOST} '

                    docker pull ${DO_IMAGE}:latest

                    docker stop ${APP_NAME} || true
                    docker rm ${APP_NAME} || true

                    docker run -d \
                        --name ${APP_NAME} \
                        -p ${APP_PORT}:${APP_PORT} \
                        -e DB_PASSWORD=${DB_PASSWORD} \
                        ${DO_IMAGE}:latest

                    '
                """
            }
        }

        stage('📊 Prometheus & Grafana') {
            steps {
                sh '''
                    echo "Checking Prometheus..."
                    curl -sf http://raas-prometheus:9090/-/healthy && echo "✅ Prometheus healthy" || echo "⚠️  Prometheus unreachable"

                    echo "Checking app metrics endpoint..."
                    curl -sf http://raas-backend:8090/actuator/prometheus | head -3 && echo "✅ App metrics reachable" || echo "⚠️  App metrics not yet available"

                    echo "Checking Grafana..."
                    curl -sf http://raas-grafana:3000/api/health && echo "✅ Grafana healthy" || echo "⚠️  Grafana unreachable"
                '''
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline réussie — RaaS déployé'
        }
        failure {
            echo '❌ Pipeline échouée — vérifier les logs'
        }
    }
}
