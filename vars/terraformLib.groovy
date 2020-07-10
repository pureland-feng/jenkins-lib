#!groovy

def call(Closure body) {

    properties([
            parameters([
                    string(name: 'count', defaultValue: '1', description: ''),
                    string(name: 'cpuNums', defaultValue: '2', description: ''),
                    string(name: 'diskSize', defaultValue: '20', description: ''),
                    string(name: 'mem', defaultValue: '2048', description: ''),
                    string(name: 'vENV', defaultValue: 'dev', description: ''),
                    string(name: 'vmName', defaultValue: '', description: ''),
                    choice(choices: ['v100', 'v101'], name: 'vsHost', description: ''),
                    string(name: 'vsphereFolder', defaultValue: 'Ops-Cluster', description: ''),
            ])
    ])
    def vHostInfo = [
            v100 : [
                    host            : '172.18.3.100',
                    datastore       : 'datastore100-2'
            ],
            v101 : [
                    host            : '172.18.3.101',
                    datastore       : 'datastore101-2'
            ]
    ]
    def pipelineParams = [
            gitlabCredentialsId        : 'deploy_git_pro',
            repoUrl                    : '',
            branch                     : 'master',

            count                      : params.count,
            cpuNums                    : params.cpuNums,
            diskSize                   : params.diskSize,
            mem                        : params.mem,
            vENV                       : params.vENV,
            vmName                     : params.vmName,
            vsphereFolder              : params.vsphereFolder,
            vsHost                     : vHostInfo[params.vsHost].host,
            dataStore                  : vHostInfo[params.vsHost].datastore,
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node('master') {

        try{
            stage('0.Get code from Gitlab') {
                git branch: "${pipelineParams.branch}", credentialsId: "${pipelineParams.gitlabCredentialsId}", url: "${pipelineParams.repoUrl}"
            }

            stage('1.deploy') {

                sh """cd vsphere/dev 
                chmod +x ./tools
                ./tools -COUNT ${pipelineParams.count} -cpuNums ${pipelineParams.cpuNums} -dataStore ${pipelineParams.dataStore} -diskSize ${pipelineParams.diskSize} -mem ${pipelineParams.mem} -vENV ${pipelineParams.vENV} -vmName ${pipelineParams.vmName} -vsHost ${pipelineParams.vsHost} -vsphereFolder ${pipelineParams.vsphereFolder}
                """
            }
        }catch (exc) {
            throw exc
        }
        finally {
            deleteDir()
        }
    }

}
