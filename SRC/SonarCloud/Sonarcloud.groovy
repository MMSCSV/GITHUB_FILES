package io.bddevops.sonarcloud

import io.bddevops.common.Common
import groovy.json.JsonOutput

class Sonarcloud implements Serializable {
  def steps
  def Common
  def debug = false

  String SONARCLOUD_PROJECTKEY = "" // GitOrg_GitRepo
  String SONARCLOUD_PROJECTNAME = "" // GitRepo
  String SONARCLOUD_ORG_NAME = "" // GitOrg
  String SONARCLOUD_DOTNET_PARAMETERS = ""
  String SONARCLOUD_MSBUILD_PARAMETERS = ""
  String SONARCLOUD_CREDENTIALS = "sonar-cloud-token_sa-devops-jenkins_bdgh"
  String SONARCLOUD_URL = "https://sonarcloud.io"
  String SONARCLOUD_SCANNER_BAT = "c:\\sonar-scanner\\bin\\sonar-scanner"
  String SONARCLOUD_SCANNER_MSBUILD_EXE = "c:\\sonar-scanner\\sonar-scanner-msbuild-net46\\SonarScanner.MSBuild.exe"
  String SONARCLOUD_SCANNER_MSBUILD_DLL = "c:\\sonar-scanner\\sonar-scanner-msbuild-netcoreapp2.0\\SonarScanner.MSBuild.dll"
  String ALT_SONAR_SCANNER_MSBUILD_DLL = "C:\\sonar-scanner\\sonar-scanner-msbuild-net5.0\\SonarScanner.MSBuild.dll" // updated to point to generic 5.0 version, but can be updated from current version of 5.14.0.78575

  Sonarcloud(steps) {
    this.steps = steps
    Common = new Common(steps)
  }

  def setDebug(Boolean debug) {
    this.debug = debug
  }

  // this is the project key: git-org_git-repo
  def getProjectKey() {
    if(!SONARCLOUD_PROJECTKEY.trim()) {
      SONARCLOUD_PROJECTKEY = "${steps.GlobalVars.JKN_GIT_ORG}_${steps.GlobalVars.JKN_GIT_REPO}"
    }
    return SONARCLOUD_PROJECTKEY
  }
  
  // this is the project name: git-repo
  def getProjectName() {
    if(!SONARCLOUD_PROJECTNAME.trim()) {
      SONARCLOUD_PROJECTNAME = steps.GlobalVars.JKN_GIT_REPO
    }
    return SONARCLOUD_PROJECTNAME
  }

  def getUrl() { 
    return SONARCLOUD_URL
  }

  def getCredentials() { 
    return SONARCLOUD_CREDENTIALS
  }

  // this is the git-org
  def getOrg() { 
    def orgName = SONARCLOUD_ORG_NAME ? SONARCLOUD_ORG_NAME : steps.GlobalVars.JKN_GIT_ORG
    SONARCLOUD_ORG_NAME = orgName
    return orgName
  }

  def setUrl(String url) { 
    SONARCLOUD_URL = url
  }

  // default: GitORG_GitREPO, set if the projectKey is different in SonarCloud.io
  def setProjectKey(String projectKey) {
    SONARCLOUD_PROJECTKEY = projectKey
  }

  // default: JKN_GIT_REPO, set if the projectName is different in SonarCloud.io
  def setProjectName(String projectName) {
    SONARCLOUD_PROJECTNAME = projectName
  }

  // default: JKN_GIT_ORG, set if the organization is different in SonarCloud.io
  def setSonarOrg(String sonarOrg) {
    SONARCLOUD_ORG_NAME = sonarOrg
  }

  def setMsbuildParameters(String params) {
    SONARCLOUD_MSBUILD_PARAMETERS = params
  }

  def setDotnetParameters(String params) {
    SONARCLOUD_DOTNET_PARAMETERS = params
  }

  def setCredentials(String params) {
    SONARCLOUD_CREDENTIALS = params
  }

  def setScannerBat(String params) {
    SONARCLOUD_SCANNER_BAT = params
  }

  def setScannerMsbuildExe(String params) {
    SONARCLOUD_SCANNER_MSBUILD_EXE = params
  }

  def setScannerMsbuildDll(String params) {
    SONARCLOUD_SCANNER_MSBUILD_DLL = params
  }

  // used for non-msbuild/dotnet runs
  def execute(Map inputArgs = [:]) {
    def filesToScan = "."
    if (inputArgs.containsKey("scan")) {
        filesToScan = inputArgs["scan"]
    }
    def sonarParameters = ""
    if (inputArgs.containsKey("parameters")) {
        sonarParameters = inputArgs["parameters"]
    }
    def ignoreFile = ".sonarignore"
    if (inputArgs.containsKey("ignorefile")) {
        ignoreFile = inputArgs["ignorefile"]
    }
    def credential = getCredentials()
    if (inputArgs.containsKey("credential")) {
        credential = inputArgs["credential"]
    }
    def verbose = debug
    if (inputArgs.containsKey("verbose")) {
        verbose = inputArgs["verbose"].toBoolean()
    }

    def projectKey = getProjectKey()
    def projectName = getProjectName()
    def org = getOrg()
    // creates the project in SonarCloud if the project does not exists
    if(!Boolean.valueOf(projectExists(projectKey,org))) {
      createProject(projectKey,projectName,org)
    }

    // If it is a PR-branch, then just scans changed lines of code.
    if(steps.GlobalVars.JKN_IS_PR) {
      steps.withCredentials([steps.string(credentialsId: "${credential}", variable: 'SECRET')]) {
        def files = ignoreSonarcloudFile(ignoreFile)
        def args = "-Dsonar.organization=${org} -Dsonar.projectKey=${projectKey} -Dsonar.sources=${filesToScan} -Dsonar.host.url=${getUrl()} -Dsonar.login=${steps.SECRET} -Dsonar.scm.provider=git -Dsonar.verbose=${verbose} -Dsonar.pullrequest.branch=${steps.env.CHANGE_BRANCH} -Dsonar.pullrequest.base=${steps.env.CHANGE_TARGET} -Dsonar.pullrequest.key=${steps.env.CHANGE_ID} -Dsonar.test.exclusions=\"**/**test*\" -Dsonar.coverage.exclusions=\"**/**test*\""
        args = files ? args + " -Dsonar.exclusions=${files}" : args
        args = sonarParameters ? args + " " + sonarParameters : args
        args = filesToScan!="." ? args + " -Dsonar.projectBaseDir=${filesToScan}" : args
        steps.println """
============================================================
Starting PR Branch Scan: ${SONARCLOUD_SCANNER_BAT}
Args: ${args}
============================================================
  """
        Common.genericScriptCmd("${SONARCLOUD_SCANNER_BAT} ${args}")
      }
    }
    // If it is Not a PR-branch, then scans lines of code in whole branch.
    else {
      steps.withCredentials([steps.string(credentialsId: "${credential}", variable: 'SECRET')]) {
        def files = ignoreSonarcloudFile(ignoreFile)
        def args = "-Dsonar.organization=${org} -Dsonar.projectKey=${projectKey} -Dsonar.sources=${filesToScan} -Dsonar.host.url=${getUrl()} -Dsonar.login=${steps.SECRET} -Dsonar.scm.provider=git -Dsonar.verbose=${verbose} -Dsonar.test.exclusions=\"**/**test*\" -Dsonar.coverage.exclusions=\"**/**test*\""
        args = files ? args + " -Dsonar.exclusions=${files}" : args
        args = sonarParameters ? args + " " + sonarParameters : args
        args = filesToScan!="." ? args + " -Dsonar.projectBaseDir=${filesToScan}" : args
        steps.println """
============================================================
Starting Branch Scan: ${SONARCLOUD_SCANNER_BAT}
Args: ${args}
============================================================
  """
        Common.genericScriptCmd("${SONARCLOUD_SCANNER_BAT} ${args}")
      }
    }
  }

  // use only for msbuild or dotnet process
  // usage: Devops.Sonarcloud.scan(name:'msbuild', process:'begin', verbose:true)
  def scan(Map args) {
    def verbose = debug
    if(args.containsKey("verbose")) {
      verbose = args["verbose"]
    }

    if((args.name.toLowerCase() != 'msbuild') && (args.name.toLowerCase() != 'dotnet')) {
      steps.error("The named parameter \"name\" only supports value: msbuild or dotnet")
    }
    if((args.process.toLowerCase() != 'begin') && (args.process.toLowerCase() != 'end')) {
      steps.error("The named parameter \"process\" only supports value: begin or end")
    }

    def projectKey = getProjectKey()
    def projectName = getProjectName()
    def org = getOrg()

    // creates the project in SonarCloud if the project does not exists
    if(args.process == 'begin') {
      if(!Boolean.valueOf(projectExists(projectKey,org))) {
        createProject(projectKey,projectName,org)
      }
    }

    def processArgs = ""
    // Cloud requires credentials to run scans, so putting both 'begin' and 'end' operations in credentials enclosure
    steps.withCredentials([steps.string(credentialsId: getCredentials(), variable: 'SECRET')]) {
      def argsStart = "begin /k:\"${projectKey}\"  /o:\"${org}\" /d:sonar.login=\"${steps.SECRET}\" /d:sonar.host.url=${getUrl()}"
      def argsEnd = "end /d:sonar.login=\"${steps.SECRET}\""  
      if(verbose){
        steps.println "argsStart=${argsStart}"
      }
      // Exclude files from sonarcloud test
      def files = ignoreSonarcloudFile(".sonarignore")
      argsStart = files ? argsStart + " /d:sonar.exclusions=${files}" : argsStart

      if(args.name.toLowerCase() == 'msbuild') {
        processArgs = SONARCLOUD_SCANNER_MSBUILD_EXE
        argsStart = SONARCLOUD_MSBUILD_PARAMETERS ? argsStart + " " + SONARCLOUD_MSBUILD_PARAMETERS : argsStart
      }

      if(args.name.toLowerCase() == 'dotnet') {
        // JKN_DOTNET_EXE defaults to windows location
        def _dotnetExe = steps.GlobalVars.JKN_DOTNET_EXE
        if(Boolean.valueOf(steps.isUnix())) {
          // if in a unix environment, default to 'dotnet'
          _dotnetExe = 'dotnet'
        }
        processArgs = "\"${_dotnetExe}\" ${getNetcoreappVersion()}"
        argsStart = SONARCLOUD_DOTNET_PARAMETERS ? argsStart + " " + SONARCLOUD_DOTNET_PARAMETERS : argsStart
      }

      if(args.process.toLowerCase() == 'begin') {
        steps.println """
============================================================
Starting Scan: ${processArgs}
Args: ${argsStart}
============================================================
  """
        Common.genericScriptCmd("${processArgs} ${argsStart}")
      }

      if(args.process.toLowerCase() == 'end') {
        steps.println """
============================================================
Ending Scan: ${processArgs}
Args: ${argsEnd}
============================================================
  """
        Common.genericScriptCmd("${processArgs} ${argsEnd}")
      }
    }
  }

  /* Returns current project Sonar analysis metrics as json object
    {
      "name":"git-org_git-repo",
      "metrics":[
        {"metric":"coverage","value":"44.3"},{"metric":"complexity","value":"18322"},...
      ]
    }
   Usage: Devops.Sonarcloud.getProjectMetricsCloud(projectKey, branch, metricsCsv)
    projectKey: Sonar project name as 'git-org_git-repo'. Default to current pipeline project
    branch: The branch to get analysis from. Default to main branch in sonar cloud
    metricsCsv: The comma separated list of metrics. Default to 'new_violations,duplicated_lines_density,coverage,complexity,lines,bugs,code_smells,vulnerabilities,security_hotspots'
  */
  def getProjectMetricsCloud(String projectKey = null, String branch = null, String metricsCsv = null)
  {
    // default project to sonar cloud current project
    if(projectKey == null)
      projectKey = getProjectKey()

    // comma separated list of valid metrics in sonar: https://docs.sonarsource.com/sonarcloud/digging-deeper/metric-definitions/
    if(metricsCsv == null || metricsCsv.trim() == '')
      metricsCsv = 'new_violations,duplicated_lines_density,coverage,complexity,lines,bugs,code_smells,vulnerabilities,security_hotspots'

    steps.println("""Pulling Sonar analysis metrics:
      - Sonar Url: ${getUrl()}
      - Project name: ${projectKey}
      - Branch: ${branch}
      - Metrics: ${metricsCsv}""")

    // GET /api/measures/component?component={projectKey}&metricKeys={metrics}&branch={branch}

    def url = "${getUrl()}/api/measures/component?component=${projectKey}&metricKeys=${metricsCsv}"
    if(branch != null && branch.trim() != '')
      url += "&branch=${branch}"

    // execute the http request
    def content = ''
    steps.withCredentials([steps.string(credentialsId: getCredentials(), variable: 'SECRET')]) {
      def response = steps.httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
        customHeaders: [[name: 'Authorization', value: 'Bearer ' + steps.SECRET]],
        httpMode: 'GET',
        url: url,
        timeout: 60

      steps.println("Sonar response: ${response.toString()}, Content: ${response.content}")

      // error if not successful or not found
      if(response.status != 200) {
        def errorMsg = "Error calling httpRequest."
        steps.error(errorMsg)
      }

      content = response.content
    }

    // parse the response
    def contentJson = steps.readJSON text: content

    // define map for metrics representing the returned json object
    def metrics = []
    def analysisSummary = [name: projectKey, metrics: metrics]

    // populate all metrics in summary
    contentJson.component.measures.each {
      metrics.add([metric:it.metric, value:it.value])
    }

    // return data as json object
    return JsonOutput.toJson(analysisSummary)
  }

  def createProject(String projectKey = getProjectKey(), String projectName = getProjectName(), String org = getOrg()) {
    steps.withCredentials([steps.string(credentialsId: getCredentials(), variable: 'SECRET')]) {
      def _url = "${getUrl()}/api/projects/create?name=${projectName}&project=${projectKey}&organization=${org}"
      def response = steps.httpRequest url: _url, 
                      customHeaders: [[name: 'Authorization', value: 'Bearer ' + steps.SECRET]],
                      httpMode: 'POST', 
                      timeout: 60
      if(response.status != 200) {
        def errorMsg = "Error creating Sonarcloud Project: \n ${response.status}\n ${response.content}"
        steps.error(errorMsg)
      }
    }
  }

  def deleteProject(String projectKey = getProjectKey()) {
    steps.withCredentials([steps.string(credentialsId: getCredentials(), variable: 'SECRET')]) {
      def _url = "${getUrl()}/api/projects/delete?project=${projectKey}"
      def response = steps.httpRequest url: _url, 
                      customHeaders: [[name: 'Authorization', value: 'Bearer ' + steps.SECRET]],
                      httpMode: 'POST', 
                      timeout: 60
      if(response.status != 204) {
        def errorMsg = "Error deleting Sonarcloud Project: \n ${response.status}\n ${response.content}"
        steps.error(errorMsg)
      }
    }
  }

  def projectExists(String projectKey = getProjectKey(), String projectOrg = getOrg() ) {
    steps.withCredentials([steps.string(credentialsId: getCredentials(), variable: 'SECRET')]) {
      def _url = "${getUrl()}/api/projects/search?projects=${projectKey}&organization=${projectOrg}"
      def response = steps.httpRequest url: _url, 
                      customHeaders: [[name: 'Authorization', value: 'Bearer ' + steps.SECRET]],
                      httpMode: 'GET', 
                      timeout: 60
      if(response.status != 200) {
        def errorMsg = "Error calling httpRequest."
        steps.error(errorMsg)
      }
      def contentJson = steps.readJSON text: response.content
      if(contentJson.components.size() > 0) {
        if(contentJson.components.key[0] == projectKey) {
          return true
        }
      } else {
        return false
      }
    }
  }

  def ignoreSonarcloudFile(String fileName) {
    // Exclude files from sonarcloud test
    if (steps.fileExists(fileName)) {
      def ignoreFiles = steps.readFile(file: fileName)
      ignoreFiles = ignoreFiles.split('\n')
      def files = ""
      ignoreFiles.each {
        def line = it.trim()
        if (line) {
          files = files ? files + ',' + line : line
        }
      }
      return files
    }
    return
  }

  private def getNetcoreappVersion(){
    if (steps.fileExists(ALT_SONAR_SCANNER_MSBUILD_DLL)) {
      return ALT_SONAR_SCANNER_MSBUILD_DLL
    } else if(steps.fileExists(SONARCLOUD_SCANNER_MSBUILD_DLL)) {
      return SONARCLOUD_SCANNER_MSBUILD_DLL
    }else {
      return 'sonarscanner' // use global installation 
    }
  }
}
