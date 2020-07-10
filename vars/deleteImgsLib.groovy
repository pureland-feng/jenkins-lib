#!groovy

def call(Closure body) {

    body.resolveStrategy = Closure.DELEGATE_FIRST
    body()

    node('master') {

        try {
            stage('5.Docker prune') {

                sh "docker system prune -a -f"
            }
        }catch (exc) {
            throw exc
        }
        finally {
            deleteDir()
        }
    }

}
