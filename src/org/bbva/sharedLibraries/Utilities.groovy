package org.bbva.sharedLibraries

@GrabResolver(name='CENTRAL', root='http://cibartifactory.igrupobbva:8084/artifactory/repo1' , m2Compatible=true)
@Grapes(
    @Grab(group='org.jfrog.artifactory.client', module='artifactory-java-client-services', version='0.16')
)

import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClient;
import org.jfrog.artifactory.client.model.Folder;

/**
* Class that implements utilities used by JenkinsFile
*/
class Utilities implements Serializable {
    
	def steps
	def envVars = Jenkins.instance.getGlobalNodeProperties()[0].getEnvVars() 
    
	/**
	 * Constructor
	 * 
	 * @param steps
	 */
	Utilities(steps) {
		this.steps = steps
	}
    
    /**
	 * Inform QA value in to a Deployment issue
	 */
    def informQA(script, JOB){
        //def BN = script.env.BUILD_NUMBER
        //def String ISSUE = new File('/usr/local/pr/jenkins/CJE_1X/scripts/DEVOPS/tmp/'+JOB+BN+'.txt').text
        //def JN_PADRE = script.env.JOB_NAME.split("/")[0]
        //def BRANCH = script.env.BRANCH_NAME
        //def RUTA_LOGS = script.env.JENKINS_HOME+'/jobs/'+JN_PADRE+'/branches/'+BRANCH+'/builds/'+BN+'/log'
        //def RS = envVars['RUTA_SCRIPT_DEVOPS']
        //steps.sh "grep -o 'Codigo:[0-9]' -m1 ${RUTA_LOGS} >> ${script.env.JENKINS_HOME}/scripts/DEVOPS/tmp/${JOB}${BN}_QA.txt"
        
        //def String QA_STATUS = new File('/usr/local/pr/jenkins/CJE_1X/scripts/DEVOPS/tmp/'+JOB+BN+'_QA.txt').text
        
        //steps.sh  "${RS}/updateInformationSQA.sh ${ISSUE} ${QA_STATUS}"
    }
	
	/**
	 * Execute the SQA´s script to analize code quality
	 * 
	 * @param ARQ
	 * @param LENGUAJE
	 * @param ARTIFACTORY_USER
	 * @param ARTIFACTORY_PASSWORD
	 * @param UUAA
	 * @param JOB
	 * @param BRANCH
	 */
    def executeQA(ARQ, LENGUAJE, ARTIFACTORY_USER, ARTIFACTORY_PASSWORD, UUAA, JOB, BRANCH){
        //steps.sh "/usr/local/pr/jenkins/scripts/SQA/lanzador_java.sh $ARQ $LENGUAJE $ARTIFACTORY_USER $ARTIFACTORY_PASSWORD $UUAA $JOB $BRANCH"
    }
    
	/**
	 * Return number of the releases in a repo.
	 * 
	 * @param script.- executable that invoke this method.
	 * @param UUAA.- Application´s code
	 * @return childrenItemsSize.
	 */
    @NonCPS
    def releaseNumber(script, UUAA){
        def REPO = UUAA+"_Artifacts"
        def FOLDER = script.env.JOB_NAME
        def childrenItemsSize
        Artifactory artifactory = ArtifactoryClient.create("http://cibartifactory.igrupobbva:8084/artifactory/repo1", "xparti1p", "AP41k1dGkPms1ZpncGyB41xCFB6")
        try {
            Folder folder = artifactory.repository(REPO).folder(FOLDER).info()
            childrenItemsSize = folder.getChildren().size()
        } catch (Exception e){
            childrenItemsSize = 0
        }
        return childrenItemsSize
    }
    
	/**
	 * Check if it´s possible deploy a new release.
	 * 
	 * @param script.- executable that invoke this method.
	 * @param UUAA.- Application´s code
	 * @param DEPLOYMENT_TASK.- Condition that indicate if is a deploy with JIRA issue.
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @return OK
	 */
    def Boolean validateRelease(script, UUAA, DEPLOYMENT_TASK, AUTO_DEPLOY){
        def releases = releaseNumber(script, UUAA)
        def OK = true
		if(DEPLOYMENT_TASK == "SI"){
			if(releases  >= 10 ){
				OK = false
			} 	
		}else if(AUTO_DEPLOY == "NO") {
			OK = false
		}
		return OK
    }
    
	/**
	 * Download the application´s code in the job´s workspace.
	 * 
	 * @param script.- executable that invoke this method.
	 * @param repoURL.- URL of the git repository that contains the code.
	 * @param credentials_ID.- credentials
	 */
    def downLoadSources(script, repoURL, credentials_ID){
        def JH = envVars['JENKINS_HOME']
        def WSC = script.env.JOB_NAME.replace("/", "_")
        steps.git  branch: script.env.BRANCH_NAME, credentialsId: credentials_ID, url: repoURL
        steps.sh "rm -rf ${JH}/workspace/${WSC}*"
    }
    
	/**
	 * Deploy the artifact generated in a Artifactory´s build of the application.
	 * 
	 * @param script.- executable that invoke this method.
	 * @param UUAA.- Application´s code
	 * @param RUTA_BINARIO
	 * @param TIPO_PASE
	 * @param JOB.- Job Jenkins name
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @param DEPLOYMENT_TASK.- Condition that indicate if is a deploy with JIRA issue.
	 */
	def subidaArtifactory(script, UUAA, RUTA_BINARIO, TIPO_PASE, JOB, AUTO_DEPLOY, DEPLOYMENT_TASK){
        def RS = envVars['RUTA_SCRIPT_DEVOPS']
		if(script.env.BRANCH_NAME == "master" || script.env.BRANCH_NAME.contains("release") || script.env.BRANCH_NAME.contains("hotfix")){
			if(DEPLOYMENT_TASK == "SI"){
				if(validateRelease(script, UUAA, DEPLOYMENT_TASK, AUTO_DEPLOY)){
					steps.sh " echo Valor de RUTA SCRIPTS ${RS}"
					steps.sh " echo Valor de RUTA SCRIPTS ${RUTA_BINARIO}"
					steps.sh "${RS}/subida_artifactory.sh ${UUAA} ${RUTA_BINARIO} ${TIPO_PASE} ${JOB}"
				} else{	
					steps.sh "echo Se ha superado el número de releases."
					steps.sh "exit -1"
				}
			}
		}else{
		    steps.sh "${RS}/subida_artifactory.sh ${UUAA} ${RUTA_BINARIO} ${TIPO_PASE} ${JOB}"
		}
    }
	
	/**
	 * Create the JIRA issue relate with the change.
	 * 
	 * @param script.- executable that invoke this method.
	 * @param JOB.- Job Jenkins name
	 * @param LAST_TAG
	 * @param JIRA_SERVICE_USER
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @param UUAA.- Application´s code
	 * @param PROJECT.- Project defined in CA Release Aplication for application.
	 * @param AFFECTED_VERSION.
	 */
    def String createDeploymentIssue(script, JOB, LAST_TAG, JIRA_PROJECT, JIRA_SERVICE_USER, AUTO_DEPLOY, UUAA, PROJECT, AFFECTED_VERSION){
		def String ISSUE = null
		if(script.env.BRANCH_NAME == "master" || script.env.BRANCH_NAME.contains("release") || script.env.BRANCH_NAME.contains("hotfix")){
			steps.withCredentials([[$class: 'StringBinding', credentialsId: 'CA_RA', variable: 'CLAVE']]) {
				def RS = envVars['RUTA_SCRIPT_DEVOPS']
				def BN = script.env.BUILD_NUMBER
				steps.sh  "${RS}/createDeploymentLinkedIssue.sh ${JOB} ${BN} ${LAST_TAG} ${JIRA_PROJECT} ${JIRA_SERVICE_USER} ${AUTO_DEPLOY} ${UUAA} ${PROJECT} ${AFFECTED_VERSION} "
				def File f = new File('/usr/local/pr/jenkins/CJE_1X/scripts/DEVOPS/tmp/'+JOB+BN+'.txt')
				ISSUE = f.text
			}
		}
		return ISSUE
    }
	
	/**
	 * Deploy the application
	 * 
	 * @param script.- executable that invoke this method.
	 * @param JOB.- Job Jenkins name
	 * @param BUILD_NUMBER
	 * @param PROJECT.- Project defined in CA Release Aplication for application.
	 * @param UUAA.- Application´s code
	 * @param ENTORNO
	 */
	def deployApplication(script, JOB, BUILD_NUMBER, CATEGORY, PROJECT, UUAA, ENTORNO){
		steps.withCredentials([[$class: 'StringBinding', credentialsId: 'CA_RA', variable: 'CLAVE']]) {
		    def Date date = new Date()
		    String FECHA = date.format("HHmmddMM")
            def RS = envVars['RUTA_SCRIPT_DEVOPS']
            def BN = script.env.BUILD_NUMBER
			steps.sh  "${RS}/deployApplication.sh ${BUILD_NUMBER}_${FECHA} ${JOB}--${BUILD_NUMBER} ${CATEGORY} ${PROJECT} ${UUAA} ${ENTORNO}"
		}
	}
	
	/**
	 * Manage the way to deploy
	 * 
	 * @param script.- executable that invoke this method.
	 * @param ISSUE_KEY
	 * @param UUAA.- Application´s code
	 * @param AUTO_DEPLOY.- Indicate if deploy automatically.
	 * @param DEPLOYMENT_TASK.- Condition that indicate if is a deploy with JIRA issue.
	 * @param JOB.- Job Jenkins name
	 * @param BUILD_NUMBER
	 * @param PROJECT.- Project defined in CA Release Aplication for application.
	 * @param ENTORNO
	 */
	def deployApp(script, ISSUE_KEY, UUAA, AUTO_DEPLOY, DEPLOYMENT_TASK, JOB, BUILD_NUMBER, PROJECT, ENTORNO){
		try {
			if(AUTO_DEPLOY == "SI"){
				if(DEPLOYMENT_TASK == "SI"){
				    def RS = envVars['RUTA_SCRIPT_DEVOPS']
					steps.sh  "${RS}/transitionDeploymentIssue.sh ${ISSUE_KEY} ${UUAA}"
				} else {
					deployApplication(script, JOB, BUILD_NUMBER, "DEVELOP", PROJECT, UUAA, ENTORNO)
				}
			}
		} catch (err){
			throw err
			sh "exit -1"
		}
	}	
}