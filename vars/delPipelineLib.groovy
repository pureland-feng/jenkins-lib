#!groovy
import com.wonder.DeployStages

def call(Closure body) {
    properties([
            parameters([
                    string(name: 'image', description: 'Image to deploy'),
                    string(name: 'tag', defaultValue: 'latest', description: 'Tag of image to deploy'),
                    string(name: 'namespace', defaultValue: 'dev', description: 'Namespace to deploy'),
                    booleanParam(name: 'delete', defaultValue: false, description: 'Delete deployment?'),
            ])
    ])

    // evaluate the body block, and collect configuration into the object
    def pipelineParams = [
            gitlabCredentialsId        : '',
            dockerRegistryCredentialsId: "docker-registry-login",
            dockerRegistry             : '127.0.0.1',
            imagePrefix                : '127.0.0.1/dir',
            k8sApiServerUrl            : 'https://127.0.0.1:6443',

            deployImage                : params.image,
            deployTag                  : params.tag,
            deployNamespace            : params.namespace,

            delete                     : params.delete,
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    if (!"$pipelineParams.deployImage"?.trim()) {
        println("Invalid image")
        return
    }

    node('master') {
        try{
            def pipeline = new DeployStages().deployStages(pipelineParams)
            pipeline()
        }catch (exc) {
            throw exc
        }
        finally {
            deleteDir()
        }
    }

}
