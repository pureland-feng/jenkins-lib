#!groovy
import com.wonder.DeployStages

def call(Closure body) {

    def pipelineParams = [
            mavenSettingsXmlFileId     : '',
            gitlabCredentialsId        : '',
            dockerRegistryCredentialsId: "docker-registry-login",
            dockerRegistry             : '127.0.0.1',
            imagePrefix                : '127.0.0.1',
            imagePrefixUat             : '127.0.0.1',
            k8sApiServerUrl            : 'https://127.0.0.1:6443',

            repoUrl                    : '',
            branch                     : 'dev',
            commitId                   : '',
            deployToUat                : '',

            deployNamespace            : 'dev',
            deployApolloMeta           : 'http://127.0.0.1:8080',
            apolloNamespace            : 'application',
            targetModules              : '',
            dockerFileInRoot           : false,
            yamlBranch                 : 'dev',
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()


    def modules = []
    node('master') {

        try{
            stage('Get code from Gitlab') {
                git branch: "${pipelineParams.branch}", credentialsId: "${pipelineParams.gitlabCredentialsId}", url: "${pipelineParams.repoUrl}"
                if ("${pipelineParams.commitId}"?.trim()) {
                    sh "git checkout -b temp ${pipelineParams.commitId}"
                }
                echo "#################################################"
                sh "git log -n 5"
                echo "#################################################"
            }

            stage('Decide what modules to be built') {
                if ("${pipelineParams.targetModules}"?.trim()) {
                    "${pipelineParams.targetModules}".split(",").each {
                        modules.push("${it}".trim())
                    }
                } else {
                    def files = findFiles(glob: '*/Dockerfile')
                    for (file in files) {
                        echo "Adding ${file.path}"
                        modules.add("${file.path}".replaceAll("/.*", ""))
                    }
                }
            }
            if (modules.size() <= 0) {
                echo "Modules not found"
                return
            }

            def stepsForParallel = modules.collectEntries {
                ["${it}": transformIntoStep(it, pipelineParams)]
            }

            parallel stepsForParallel

        }catch (exc) {
            throw exc
        }
        finally {
            deleteDir()
        }
    }
}

def transformIntoStep(targetModule, pipelineParams) {

    return {
        def imageName
        def buildTag
        def buildVersion
        stage('Prepare') {

            pipelineParams.each { key, val -> println "Parameter -> ${key}:${val}" }

            if (pipelineParams.deployToUat) {
                pipelineParams.put("imagePrefix", pipelineParams.imagePrefixUat)
            }
            imageName = "${pipelineParams.imagePrefix}/${targetModule}"
            echo "Docker image name: ${imageName}"

            buildTag = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
            echo "build tag: ${buildTag}"
        }

        stage('Run build and unit tests') {

                if(pipelineParams.dockerFileInRoot){
                    buildVersion = sh script: './mvnw help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true
                    sh "./mvnw clean package -Dmaven.test.skip"
                }else {
                    buildVersion = sh script: './mvnw help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true
                    sh "./mvnw clean package -Dmaven.test.skip -pl ${targetModule} -am"
                }
        }

        def builtImage
        stage('Build docker images') {
            if(pipelineParams.dockerFileInRoot){
                dir("${WORKSPACE}") {

                    builtImage = docker.build("${imageName}:${buildTag}", "--network host --build-arg JAR_FILE=target/${targetModule}-${buildVersion}.jar .")
                }
            }else {
                dir("${WORKSPACE}/${targetModule}") {


                    builtImage = docker.build("${imageName}:${buildTag}", "--network host --build-arg JAR_FILE=target/${targetModule}-${buildVersion}.jar .")
                }
            }
        }
        stage('Publish Docker images [and Helm Chart]') {

            sh "echo ${pipelineParams.dockerRegistry}"
            docker.withRegistry(pipelineParams.dockerRegistry, pipelineParams.dockerRegistryCredentialsId) {

                builtImage.push()
                builtImage.push('latest')

            }

        }

        stage("Deploy") {

            if(!pipelineParams.deployToUat){

                echo 'Deploy to ${pipelineParams.deployNamespace}'

                withKubeConfig(credentialsId: 'jenkins-robot-credential', serverUrl: pipelineParams.k8sApiServerUrl) {
                    pipelineParams.put("deployImage", imageName)
                    pipelineParams.put("deployTag", buildTag)
                    def pipeline = new DeployStages().deployStages(pipelineParams)
                    pipeline()
                }

            }

        }
    }
}
