package io.bddevops.artifactory

import io.bddevops.common.Common
import io.bddevops.git.Git
import io.bddevops.jenkins.JENKINS

class RT implements Serializable {
  def steps
  def Common
  def Git
  def JENKINS

  RT(steps) {
    this.steps = steps

    Common = new Common(steps)
    Git = new Git(steps)
    JENKINS = new JENKINS(steps)
  }

  def callPlugin(String plugin, String args, String mode = 'GET', Boolean verbose = false) {
    def rtUrl = "${steps.GlobalVars.JKN_ARTIFACTORY_SOURCE}/api/plugins/execute/${plugin}"
    def params = args ? "?params=${args};" : ""
    if (verbose) { steps.println "params: " + params }
    def response = steps.httpRequest(
        authentication: steps.GlobalVars.JKN_ARTIFACTORY_USER, 
        url: rtUrl + params, 
        httpMode: mode,
        validResponseCodes: '100:500',
        timeout: 60
    )
    
    if (verbose) { 
      steps.println "Status: " + response.status
      steps.println "Content: " + response.content
    }
    def _responseContent = response.content ? response.content : "\"\""

    return "{\"status\":\"${response.status}\",\"content\":${_responseContent}}"
  }

  def callApi( String api, String body = "", String httpmode = "", String accepttype = "APPLICATION_JSON", String contenttype = "TEXT_PLAIN", Boolean verbose = false ) {
    def rtUrl = "${steps.GlobalVars.JKN_ARTIFACTORY_SOURCE}/api/${api}"
    def _httpmode = httpmode ? httpmode : "GET"
    def _accepttype = accepttype ? accepttype : "APPLICATION_JSON"
    def _contenttype = contenttype ? contenttype : "TEXT_PLAIN"
    def response = steps.httpRequest(
        url: rtUrl, 
        authentication: steps.GlobalVars.JKN_ARTIFACTORY_USER,
        httpMode: _httpmode, 
        acceptType: _accepttype, 
        contentType: _contenttype, 
        requestBody: body, 
        validResponseCodes: '100:500',
        timeout: 60
    )
    if( verbose ) {
      steps.println response 
    }
    def _responseContent = response.content ? response.content: "\"\""
    return "{\"status\":\"${response.status}\",\"content\":${_responseContent}}"
  }

  /* **********************************************
    Initialize Artifactory server instance and properties
  */
  def initialize( String rtServerName = steps.GlobalVars.JKN_ARTIFACTORY_NAME, String buildName = "", String buildNumber = "", Boolean verbose = false ) {
    def _rtUploadServer = ""
    def _rtUploadServerUrl = ""
    def _rtServerName = rtServerName
    def _rtBuildName = buildName 
    if( !_rtBuildName ) {
      _rtBuildName = steps.GlobalVars.JKN_BUILD_MULTI_NAME ? "${steps.GlobalVars.JKN_BUILD_NAME}_${steps.GlobalVars.JKN_BUILD_MULTI_NAME}" : steps.GlobalVars.JKN_BUILD_NAME
      _rtBuildName = steps.GlobalVars.JKN_VERSION_TARGETVERSION ? "${_rtBuildName}_${steps.GlobalVars.JKN_VERSION_TARGETVERSION}" : _rtBuildName
    }
    def _fullVersion = buildNumber ? buildNumber : steps.GlobalVars.JKN_VERSION_FULLVERSION

    if( !steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO || steps.GlobalVars.JKN_BUILD_MULTI_NAME ) {
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILES = ""
      steps.println "Initializing Artifactory Server: ${_rtServerName}"
      _rtUploadServer = steps.Artifactory.server _rtServerName

      steps.println "THIS IS THE CURRENT UPLOAD SERVER: ${_rtUploadServer}"
      def SERVER_URL = _rtUploadServer.getUrl()
      
      def rt_base_url = SERVER_URL.endsWith("/artifactory") ? SERVER_URL[0..-13] : SERVER_URL
      steps.println "rt_base_url=${rt_base_url}"
      
      _rtUploadServerUrl = "${rt_base_url.replace('.cfnp.local:8081','.bddevops.io')}/ui/builds/${_rtBuildName}/${_fullVersion}"
      steps.println "THIS IS THE CURRENT URL: ${_rtUploadServerUrl}"
      
      steps.GlobalVars.JKN_ARTIFACTORY_NAME = _rtServerName
      steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO = steps.Artifactory.newBuildInfo()
      steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO.name = _rtBuildName
      steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO.number = _fullVersion
      steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME = _rtBuildName
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER = _rtUploadServer
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER_URL = _rtUploadServerUrl

      steps.env.devops_job_name = steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME
      steps.env.devops_build_tag = steps.GlobalVars.JKN_BUILD_TAG
      steps.env.devops_build_url = _rtUploadServerUrl
      steps.env.devops_promotion_template = steps.GlobalVars.JKN_PROMOTION_TEMPLATE_TYPE
      steps.env.build_sourcecode = steps.GlobalVars.JKN_SCM_URL
      
    }
  }

  /* **********************************************
    Initialize Artifactory server using closure
  */
  def initialize(Closure body){
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "rtservername, buildname, buildnumber, verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }

    def _targetVersion = ""
    def _buildVersion = ""
    def _rtBuildName = ""

    def _rtServerName = steps.GlobalVars.JKN_ARTIFACTORY_NAME
    if( params.find { it.key == 'rtservername' } != null ) {
      _rtServerName = params.rtservername
    }

    // this is the Artifactory BuildInfo Name
    def _buildname = ""
    if( params.find { it.key == 'buildname' } != null ) {
      _buildname = params.buildname
    }

    // this is the Artifactory BuildInfo Number
    def _buildnumber = ""
    if( params.find { it.key == 'buildnumber' } != null ) {
      _buildnumber = params.buildnumber
    }

    Boolean _verbose = false
    if( params.find { it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }

    if( _verbose ) { 
      steps.println "rtservername=${_rtServerName}, buildname=${_buildname}, buildnumber=${_buildnumber}"
    }

    // if the BuildNumber exists, it probably means that we want to create an Artifactory BbuildInfo with the Name and Number
    if( _buildnumber ) {
      def finder = (_buildnumber =~ /([\d+\.]+)\.(\d+).*$/)
      if( finder.matches() ) {
        _targetVersion = finder.group(1)
        _buildVersion = finder.group(2)
      }
      finder = null
      
      if( _verbose ) {
        steps.println "targetVersion=${_targetVersion}"
        steps.println "buildVersion=${_buildVersion}"
      }

      // Overwrite Globals
      String defaultBuildName = steps.GlobalVars.JKN_BUILD_MULTI_NAME ? "${steps.GlobalVars.JKN_BUILD_NAME}_${steps.GlobalVars.JKN_BUILD_MULTI_NAME}" : steps.GlobalVars.JKN_BUILD_NAME
      defaultBuildName = _targetVersion ? "${defaultBuildName}_${_targetVersion}" : steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME
      _rtBuildName = _buildname ? _buildname : defaultBuildName

      if( _verbose ) {
        steps.println "inputBuildName=${_rtBuildName}, defaultBuildName=${defaultBuildName}"
      }

      steps.GlobalVars.JKN_VERSION_TARGETVERSION = _targetVersion
      steps.GlobalVars.JKN_VERSION_BUILDVERSION = _buildVersion
      steps.GlobalVars.JKN_VERSION_RELEASE = (_targetVersion && _buildVersion) ? "${_targetVersion}.${_buildVersion}" : steps.GlobalVars.JKN_VERSION_RELEASE
      steps.GlobalVars.JKN_VERSION_PRERELEASE = steps.GlobalVars.JKN_VERSION_PRERELEASE ? steps.GlobalVars.JKN_VERSION_PRERELEASE : steps.GlobalVars.JKN_VERSION_RELEASE
      steps.GlobalVars.JKN_VERSION_FULLVERSION = steps.GlobalVars.JKN_VERSION_FULLVERSION ? steps.GlobalVars.JKN_VERSION_FULLVERSION : steps.GlobalVars.JKN_VERSION_RELEASE
    }

    if( _verbose ){
      steps.println "steps.GlobalVars.JKN_VERSION_TARGETVERSION=${steps.GlobalVars.JKN_VERSION_TARGETVERSION}"
      steps.println "steps.GlobalVars.JKN_VERSION_FULLVERSION=${steps.GlobalVars.JKN_VERSION_FULLVERSION}"
    }

    initialize( _rtServerName, _rtBuildName, _buildnumber )
  }

  def setRepository(String repo = "") {
    steps.GlobalVars.JKN_ARTIFACTORY_REPO = repo
  }

  def setArtifactoryUploadPath(String path = "" ) {
    def _path = path
    if(!_path) {
      _path = "${steps.GlobalVars.JKN_GIT_REPO}/${steps.GlobalVars.JKN_BRANCH_NAME}"
      _path = steps.GlobalVars.JKN_VERSION_TARGETVERSION ? "${steps.GlobalVars.JKN_GIT_REPO}/${steps.GlobalVars.JKN_BRANCH_NAME}/${steps.GlobalVars.JKN_VERSION_TARGETVERSION}" : _path
      _path = "${_path}/{1}/"
    }
    steps.println "Setting Artifactory Upload Path to: ${_path}"
    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH = _path
  }

  def getArtifactoryUploadPath() {
    return steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH
  }

  def setArtifactoryPackageGrouping( String grouping = "" ) {
    steps.GlobalVars.JKN_ARTIFACTORY_PACKAGE_GROUPING = grouping
  }

  def getArtifactoryPackageGrouping() {
    return steps.GlobalVars.JKN_ARTIFACTORY_PACKAGE_GROUPING
  }

  def setArtifactRetentionPolicy(Boolean enabled = true ) {
    steps.GlobalVars.JKN_ARTIFACTORY_ENABLE_RETENTION_POLICY = enabled
  }

  def setRetentionPolicy(Boolean bool = true) {
    steps.GlobalVars.JKN_ARTIFACTORY_ENABLE_RETENTION_POLICY = bool
  }

  def addCustomProperties(String jsonPropertyList){
    def _customProps = steps.GlobalVars.JKN_ARTIFACTORY_CUSTOM_PROPERTIES
    def propertyList = steps.readJSON text: jsonPropertyList
    propertyList.each { key, value ->
      def newKey = key.toLowerCase().trim().replaceAll("\\s","")
      if(newKey.replaceAll("[^a-zA-Z0-9-_.]+","")!=newKey){
          steps.error("Special characters not allowed!")
      }
      if (newKey != key){
          steps.println "Custom Property named '${key}' changed to '${newKey}'"
      }
      def _newProp = "${newKey}=${value};"
      steps.println "Adding custom property ${_newProp}"
      _customProps = _customProps.contains( _newProp ) ? _customProps : "${_customProps}${_newProp}"
    }
    steps.GlobalVars.JKN_ARTIFACTORY_CUSTOM_PROPERTIES = _customProps
  }

  def getCustomProperties() {
    return steps.GlobalVars.JKN_ARTIFACTORY_CUSTOM_PROPERTIES
  }


/* **********************************************
  Initialize alternate Artifactory servers for artifact upload
*/
def addAltUpload(String addRtName) {
  if (addRtName){
      steps.GlobalVars.JKN_ALT_ARTIFACTORY_NAME = addRtName
      def altRt = steps.Artifactory.server addRtName
      steps.GlobalVars.JKN_ALT_ARTIFACTORY_UPLOAD_SERVER = altRt
      steps.GlobalVars.JKN_ALT_ARTIFACTORY_BUILDINFO = "build.name=${steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME};build.number=${steps.GlobalVars.JKN_VERSION_FULLVERSION}" // Needs to be added to properties at upload time.
      steps.println "Adding alternate artifactory server: $addRtName"
  }
}

  def getBuildName() {
    return steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME
  }

  /* **********************************************
    Create upload file spec for Maven builds
  */
  def uploadSpecMaven(String mavenGroupId, String mavenArtifactId, String artifactoryGrouping = '*') {
    def mavenGroupIdFolders = mavenGroupId.replace(".", "/")
    setArtifactoryPackageGrouping( artifactoryGrouping )
    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH = steps.GlobalVars.JKN_VERSION_TARGETVERSION ? "${mavenGroupIdFolders}/${mavenArtifactId}/${steps.GlobalVars.JKN_GIT_REPO}/${steps.GlobalVars.JKN_BRANCH_NAME}/${steps.GlobalVars.JKN_VERSION_TARGETVERSION}/${steps.GlobalVars.JKN_VERSION_FULLVERSION}/${mavenArtifactId}-${steps.GlobalVars.JKN_VERSION_FULLVERSION}.aar" : "${mavenGroupIdFolders}/${mavenArtifactId}/${steps.GlobalVars.JKN_GIT_REPO}/${steps.GlobalVars.JKN_BRANCH_NAME}/${steps.GlobalVars.JKN_VERSION_FULLVERSION}/${mavenArtifactId}-${steps.GlobalVars.JKN_VERSION_FULLVERSION}.aar"
  }

  /* **********************************************
    Create upload file spec for NPM builds
  */
  def uploadSpecNpm(String rtFiles = "./*.tgz", String rtRepo = "", String artifactoryGrouping = "(*)-(${steps.GlobalVars.JKN_VERSION_FULLVERSION})", Boolean verbose = false) {
    setArtifactoryPackageGrouping( artifactoryGrouping )

    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH = steps.GlobalVars.JKN_VERSION_TARGETVERSION ? "${steps.GlobalVars.JKN_GIT_REPO}/${steps.GlobalVars.JKN_BRANCH_NAME}/${steps.GlobalVars.JKN_VERSION_TARGETVERSION}/${steps.GlobalVars.JKN_NPM_NAME}/" : "${steps.GlobalVars.JKN_GIT_REPO}/${steps.GlobalVars.JKN_BRANCH_NAME}/${steps.GlobalVars.JKN_NPM_NAME}/"
    
    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH = steps.GlobalVars.JKN_NPM_SCOPE ? "${steps.GlobalVars.JKN_NPM_SCOPE}/${steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH}" : steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH
    
    // reset JKN_NPM_SCOPE so it does not persist for other uploads
    steps.GlobalVars.JKN_NPM_SCOPE = ""

    def _rtRepo = rtRepo
    if(_rtRepo=='') {
      _rtRepo = steps.GlobalVars.JKN_ARTIFACTORY_REPO ? steps.GlobalVars.JKN_ARTIFACTORY_REPO : "${steps.GlobalVars.JKN_GIT_ORG.toLowerCase()}-npm"
    }
    uploadSpec(_rtRepo,rtFiles,artifactoryGrouping,verbose)
    uploadArtifacts()
  }

  /* **********************************************
    Custom upload for internalized choco packages
  */
  def uploadInternalizedChocoPkgs( String rtFiles = "internalize-in/**/*.nupkg", String grouping = "(*)" ) {
    def _rtRepo = "internalized-nuget"
    def _uploadPath = "{1}/"

    setArtifactRetentionPolicy( false )
    setArtifactoryUploadPath( _uploadPath )
    uploadSpec(_rtRepo, rtFiles, grouping)
    uploadArtifacts()
  }

  /* **********************************************
    Custom upload for already internalized (internal) choco packages
  */
  def uploadInternalChocoPkgs( String rtFiles = "internalize-source/**/*.nupkg", String uploadPath = ".", String grouping = "(*)" ) {
    def _rtRepo = "internalized-nuget"
    def _uploadPath = "${uploadPath}/{1}/"

    setArtifactRetentionPolicy( false )
    setArtifactoryUploadPath( _uploadPath )
    uploadSpec(_rtRepo, rtFiles, grouping)
    uploadArtifacts()
  }

  /* **********************************************
    Create upload file specs
  */
  def uploadSpec(String rtRepo, String rtFiles = "**/*.nupkg", String artifactoryGrouping = "", Boolean verbose = true ) {
    def rtProps = "devops_promotion_template=${steps.GlobalVars.JKN_PROMOTION_TEMPLATE_TYPE};devops_job_name=${steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME};label=${steps.GlobalVars.JKN_BUILD_TAG};devops_build_tag=${steps.GlobalVars.JKN_BUILD_TAG};devops_build_url=${steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER_URL.replace(":8081",":8082")};"
    rtProps = steps.GlobalVars.JKN_NPM_DEPENDENCIES ? rtProps + "devops_npm_dependencies=${steps.GlobalVars.JKN_NPM_DEPENDENCIES};" : rtProps
    rtProps = steps.GlobalVars.JKN_ARTIFACTORY_CUSTOM_PROPERTIES ? rtProps + steps.GlobalVars.JKN_ARTIFACTORY_CUSTOM_PROPERTIES : rtProps
    def _rtRepo = rtRepo ? rtRepo.toLowerCase() : "${steps.GlobalVars.JKN_ARTIFACTORY_REPO.toLowerCase()}"
    // ensure that the migrated git orgs still map to original artifactory repos. New github.com orgs will use its new name
    steps.println "UploadSpec Repo Name: " + _rtRepo
    if (_rtRepo.startsWith('bd-')) {
      _rtRepo = Common.getOnPremRtOrg(_rtRepo)
      steps.println "Updated UploadSpec Repo Name for on-prem Artifactory: " + _rtRepo
    }
    verifyRepository( _rtRepo, true )
    def rtRepoPath = steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH

    // if rtRepoPath does not exists, create the path
    if(!rtRepoPath) {
      // start with gitRepo and gitBranch
      def _rtRepoPath = "${steps.GlobalVars.JKN_GIT_REPO}/${steps.GlobalVars.JKN_BRANCH_NAME}"
      // if multibuilds are enabled in a single pipeline, add the multibuild name
      _rtRepoPath = steps.GlobalVars.JKN_BUILD_MULTI_NAME ? "${_rtRepoPath}/${steps.GlobalVars.JKN_BUILD_MULTI_NAME}" : _rtRepoPath
      // if no targetVersion is set, then deploy into a builds folder for folder structure consistency
      _rtRepoPath = steps.GlobalVars.JKN_VERSION_TARGETVERSION ? "${_rtRepoPath}/${steps.GlobalVars.JKN_VERSION_TARGETVERSION}" : "${_rtRepoPath}/builds"
      // for npm scoped packages
      _rtRepoPath = steps.GlobalVars.JKN_NPM_SCOPE ? "${steps.GlobalVars.JKN_NPM_SCOPE}/${_rtRepoPath}/${steps.GlobalVars.JKN_NPM_NAME}" : _rtRepoPath
      
      // grouping
      rtRepoPath = "${_rtRepoPath}/{1}/"
    }

    if ( verbose ) { steps.println "rtRepoPath: ${rtRepoPath}" }
 
    if (!rtRepoPath.endsWith('/')) {
      def msg = "Added '/' to end of Artifactory target. Please correct uploadSpec input!"
      rtRepoPath = rtRepoPath + '/'
      steps.unstable(msg)
    }

    rtFiles = rtFiles.replaceAll('\\\\','/')
    def pathRoot
    def pathFileName
    def pathFileNameExt
    def finder = (rtFiles =~ /^(.*)\/(.*)\.(.*)$/)
    if(finder.matches()) {
      pathRoot = finder.group(1)
      pathFileName = finder.group(2)
      pathFileNameExt = finder.group(3)
    }
    finder = null // discard nonserializable value
    def rtPackagesGrouping_default = rtFiles.endsWith('.zip') ? "(*)_(${steps.GlobalVars.JKN_VERSION_FULLVERSION})" : "(*).(${steps.GlobalVars.JKN_VERSION_FULLVERSION})"
    def rtPackagesGrouping = artifactoryGrouping ? artifactoryGrouping : rtPackagesGrouping_default
    rtPackagesGrouping = getArtifactoryPackageGrouping() ? getArtifactoryPackageGrouping() : rtPackagesGrouping
    def rtPackages = "${pathRoot}/${rtPackagesGrouping}.${pathFileNameExt}"
    
    if ( verbose ) { steps.println "rtPackages: ${rtPackages}" }

    def files = steps.findFiles(glob: rtPackages)
    if (files.size() > 0) {
      steps.println """
============================================================
Files to be deployed to Artifactory
============================================================
"""
      for (def file in files) {
        steps.println "File: ${file}"
      }
    }

        def initialUploadSpec = """
    {
      "pattern": "${rtPackages}",
      "target": "${_rtRepo}/${rtRepoPath}",
      "props": "${rtProps}"
    }
    """

    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILES = steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILES ? steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILES + ',' + initialUploadSpec : initialUploadSpec
    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_PATH = ""
    setArtifactoryPackageGrouping("")
    if (steps.GlobalVars.JKN_NPM_SCOPE) {
      steps.GlobalVars.JKN_NPM_SCOPE = ""
      steps.GlobalVars.JKN_NPM_DEPENDENCIES = ""
    }
  }

  /* **********************************************
    Artifactory upload
    Default Artifactory Repo: <GitOrg>-nuget
    Set steps.GlobalVars.JKN_ARTIFACTORY_REPO prior to artifactoryInit() if deploying to a specific Artifactory Repo
    DO NOT SET RETENTION POLICY ON RELEASE BRANCHES. IF PROMOTED, THE BUILDS WILL STILL BE REMOVED.
    This will reset the upload spec.
  */
  def uploadArtifacts() {
    if (steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILES) {
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC = """{
        "files": [
          ${steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILES}
        ]
      }"""

      // Add additional environment variables
      if (steps.GlobalVars.JKN_PACKAGE_RESTORE_LOG) {
        steps.env.devops_build_packages = formatPackageRestoreLog()
        steps.GlobalVars.JKN_PACKAGE_RESTORE_LOG = ""
      }

      // Artifactory Build Retention Policy for PR and pre-release builds
      if( (steps.GlobalVars.JKN_BRANCH_NAME.startsWith('PR-') || steps.GlobalVars.JKN_VERSION_PRERELEASE_BRANCHNAME.startsWith('pre-')) && steps.GlobalVars.JKN_ARTIFACTORY_ENABLE_RETENTION_POLICY) {
        def KEEP_MAX_BUILDS = 3
        def KEEP_MAX_DAYS = 15
        // disable retention policy
        // steps.println """
        //   BUILD RETENTION RULES FOR PULL REQUESTS:
        //     KEEP MAX BUILDS: ${KEEP_MAX_BUILDS}
        //     KEEP MAX DAYS: ${KEEP_MAX_DAYS}
        // """
        // steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO.retention maxBuilds: KEEP_MAX_BUILDS, maxDays: KEEP_MAX_DAYS, deleteBuildArtifacts: true
      }
      steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO.env.capture = true

      steps.println """
  ============================================================
  Uploading artifacts with Artifactory File Spec:
  ${steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC}
  ============================================================
      """
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER.upload spec: steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC, buildInfo: steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO

    // Upload to alternate Artifactory if set 
    if (steps.GlobalVars.JKN_ALT_ARTIFACTORY_NAME != ""){
        def finder = (steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC =~ /(?ms).*(pattern)\":\s+\"(BuildResults\/)[^z]*zip.*/) // Check if it is buildresults.zip
        def isFound = finder.matches()
        finder = null
        if(!isFound) {
           // steps.GlobalVars.JKN_ALT_ARTIFACTORY_UPLOAD_SERVER
            def jSpec = steps.readJSON text: steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC  // Adding correct build.name/number to properties.
            def jFiles = jSpec['files']
            def jContent = jFiles[0]
            def jPattern = jContent['pattern']
            def jTarget = jContent['target']
            def jProps = jContent['props']
            def newProps = "build.name=${steps.GlobalVars.JKN_BUILD_NAME};build.number=${steps.GlobalVars.JKN_VERSION_FULLVERSION};" + jProps 
            def altUploadSpec = """
              {
                "files": [
                  {
                    "pattern": "${jPattern}",
                    "target": "${jTarget}",
                    "props": "${newProps}"
                  }
                ]
              }
              """
            steps.println """
============================================================
Uploading artifacts to ${steps.GlobalVars.JKN_ALT_ARTIFACTORY_UPLOAD_SERVER.getUrl()} with File Spec:
${steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC}
============================================================
            """
            steps.GlobalVars.JKN_ALT_ARTIFACTORY_UPLOAD_SERVER.upload spec: altUploadSpec
            }        
      }

      // reset upload file spec
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILES = ""
      // reset RT deplpoy repository
      setRepository()

      // move and clean uploaded artifacts to another folder.
      if(steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC.contains("\"pattern\": \"nuget")) { cleanUploadArtifactsDir("nuget/**/*.nupkg") }
      if(steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC.contains("\"pattern\": \"pack")) { cleanUploadArtifactsDir("pack/**/*.nupkg") }
      if(steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_FILESPEC.contains("\"pattern\": \"zip")) { cleanUploadArtifactsDir("zip/**/*.zip") }
    } else {
      steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO.env.collect()
      // steps.println "NO ARTIFACTS TO UPLOAD."
    }
  }

  /* **********************************************
    Artifactory publishBuildInfo
    Pushes Artifactory Build Info
  */
  def publishBuildInfo() {
    // push Artifactory Build Info
    if(!checkBuildInfo()) {
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER.publishBuildInfo steps.GlobalVars.JKN_ARTIFACTORY_BUILDINFO
    }
    else {
      def errorMsg = """
  **********************************************
    Artifactory BuildInfo exists for this build: ${steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME}, ${steps.GlobalVars.JKN_VERSION_FULLVERSION}
    The Jenkinsfile may be uploading multiple Artifactory Build Info.
  **********************************************
  """
      steps.error(errorMsg)
    }
  }


  /* **********************************************
    Download Build Artifacts from Artifactory
  */
  def downloadBuildArtifacts(String org, String repo, String version, String outdir = "rtPackages") {
    def response = steps.readJSON text: callPlugin("getbuild", "repo=${org}/${repo};version=${version}")
    def buildName = response.content['build.name']
    def buildNumber = response.content['build.number']

    def filesDownloadJson = """
    {
      "files": [{
        "aql": {
          "items.find": {
            "\$and": [{
              "@build.name": { "\$eq": "${buildName}" },
              "@build.number": { "\$eq": "${buildNumber}" }
            }]
          }
        },
        "target": "${outdir}",
        "flat": "true"
      }]
    }
    """
    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER.download(filesDownloadJson)
  }

  def downloadLatestArtifact(String artifact, String outdir){
    def filesDownloadJson = """
        {
          "files": [{
            "aql": {
              "items.find": {
                  "name": { "\$match": "${artifact}" }
              }
            },
            "target": "${outdir}",
            "explode": "true",
            "sortBy":["modified"],
            "sortOrder":"desc",
            "limit": 1
      }]
    }
    """
    steps.println "${filesDownloadJson}"
    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER.download(filesDownloadJson)
  }

// Search Artifact method, artifact required, rtsource defaults to http://artifactory.bddevops.io
  def searchArtifacts(String artifact, String rtsource = "", Boolean verbose = false){
    def filesSearchJson = 
    """items.find(
        {
          "name": {"\$match": "${artifact}"}
        }
    )
    """
    def response = searchPost(filesSearchJson, rtsource, verbose)
    return response.content
  }

// Search Artifact uisng closure
  def searchArtifacts( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "artifact,rtsource,verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }

    if( params.find { it.key == 'artifact' } == null ) {
      steps.error("Parameter 'artifact' is required.")
    }
    def _artifact = params.artifact

    def _rtsource = ""                                         // set the default
    if( params.find { it.key == 'rtsource' } != null ) {      // if key exits
      _rtsource = "${params.rtsource}"    // build source value
    }

    Boolean _verbose = false
    if( params.find { it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }

    def response = searchArtifacts(_artifact, _rtsource, _verbose)
    return response
  }

  def getBuildArtifacts(String org, String repo, String version) {
    def artifacts = ""
    def response = steps.readJSON text: callPlugin("getbuild", "repo=${org}/${repo};version=${version}")
    artifacts = response.content.toString().contains('artifacts') ? response.content.artifacts : null
    if( artifacts == null ) { steps.println "No artifacts found for: ${org}/${repo}/${version}" }
    return artifacts
  }

  def getBuildName(String org, String repo, String version) {
    def buildname = ""
    def response = steps.readJSON text: callPlugin("getbuild", "repo=${org}/${repo};version=${version}")
    buildname = response.content.toString().contains('build.name') ? response.content['build.name'] : null
    if( buildname == null ) { steps.println "Build name not found for: ${org}/${repo}/${version}" }
    return buildname
  }

  def getBuildTag(String org, String repo, String version) {
    def buildtag = ""
    def response = steps.readJSON text: callPlugin("getbuild", "repo=${org}/${repo};version=${version}")
    buildtag = response.content.toString().contains('devops_build_tag') ? response.content.devops_build_tag : null
    if( buildtag == null ) { steps.println "Build tag not found for: ${org}/${repo}/${version}" }
    return buildtag
  }



  /* **********************************************
    Download an Item Artifact from Artifactory
  */
  def downloadItemArtifact(String packageName, String version = "latest", String outdir = "rtPackages", Boolean prerelease = false, Boolean verbose = false) {
    def packageId = packageName
    def packageVersion = version ? version : "latest"
    def downloadDir = outdir ? outdir : "rtPackages"

    def artifact = steps.readJSON text: getItemInfo(packageId,packageVersion,prerelease,verbose)
    if( artifact.error ) {
      def _msg = """
Unable to find package for PackageName: ${packageId}, Version: ${packageVersion}
Possible failures:
1) The package does not exists in Artifactory, please verify package exists, or
2) The package exists, but does not have an Artifactory BuildInfo and correct DevOps build properties set
"""
      steps.error(_msg) 
    }
    packageId = artifact.name
    packageVersion = artifact."build.number"

    if (verbose) { 
      steps.println "artifact: " + artifact 
      steps.println "packageId: " + packageId
      steps.println "packageVersion: " + packageVersion
    }

    def filesDownloadJson = """
    {
      "files": [{
        "aql": {
          "items.find": {
            "\$and": [{
              "name": { "\$eq": "${packageId}" }
            }]
          }
        },
        "target": "${downloadDir}",
        "flat": "true"
      }]
    }
    """
    steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER.download(filesDownloadJson)
    addToArtifactJson( artifact )
  }

  def downloadItemArtifact( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "packageid, version, outdir, prerelease, verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }

    if( (params.find { it.key == 'packageid' } == null) || (params.find { it.key == 'packageid' } == "") ) {
      steps.error("Parameter 'packageid' is required.")
    }
    def _packageid = params.packageid

    def _version = "latest"
    if( params.find { it.key == 'version' } != null ) {
      _version = params.version
    }
    
    def _outdir = "rtPackages"
    if( params.find { it.key == 'outdir' } != null ) {
      _outdir = params.outdir
    }

    Boolean _prerelease = false
    if( params.find { it.key == 'prerelease' } != null ) {
      _prerelease = params.prerelease
    }

    Boolean _verbose = false
    if( params.find { it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }

    downloadItemArtifact( _packageid, _version, _outdir, _prerelease, _verbose )
  }

  def downloadUnzipArtifact( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "packageid, version, outdir, prerelease, verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }

    if( (params.find { it.key == 'packageid' } == null) || (params.find { it.key == 'packageid' } == "") ) {
      steps.error("Parameter 'packageid' is required.")
    }
    def _packageid = params.packageid

    def _version = "latest"
    if( params.find { it.key == 'version' } != null ) {
      _version = params.version
    }
    
    def _outdir = "rtUnzipped"
    if( params.find { it.key == 'outdir' } != null ) {
      _outdir = params.outdir
    }

    Boolean _prerelease = false
    if( params.find { it.key == 'prerelease' } != null ) {
      _prerelease = params.prerelease
    }

    Boolean _verbose = false
    if( params.find { it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }

    def artifact = steps.readJSON text: getItemInfo( _packageid, _version, _prerelease, _verbose)
    if( artifact.error ) {
      def _msg = """
Unable to find package for PackageName: ${_packageid}, Version: ${_version}
Possible failures:
1) The package does not exists in Artifactory, please verify package exists, or
2) The package exists, but does not have an Artifactory BuildInfo and correct DevOps build properties set
"""
      steps.error(_msg) 
    }
    
    if (_verbose) { 
      steps.println "artifact: " + artifact 
      steps.println "artifact.name: " + artifact.name
    }

    downloadUnzipArtifact( artifact.name, _outdir)
  }

  def downloadUnzipArtifact(String artifactName, String outdir = "rtUnzipped") {
    def _downloadFolder = "rtDownload"
    downloadArtifact(artifactName, _downloadFolder)
    def zipfile = _downloadFolder + '\\' + artifactName
    steps.unzip(zipFile: zipfile, dir: outdir)
  }

  def downloadArtifact(List<String> artifactName, String outdir = "rtPackages") {
    def artifacts = artifactName.join(",")
    downloadArtifact(artifacts, outdir)
  }

  def downloadArtifact(String artifactName, String outdir = "rtPackages") {
    String[] artifacts = artifactName.split(',')
    for ( def artifact in artifacts ) {
      def filesDownloadJson = """
      {
        "files": [{
          "aql": {
            "items.find": {
              "name": { "\$eq": "${artifact}" }
            }
          },
          "target": "${outdir}\\",
          "flat": "true"
        }]
      }
      """
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER.download(filesDownloadJson)
    }
  }

  def downloadArtifactUsingRepoPath(List<String> repoPath, String outdir = "rtPackages", Boolean verbose = false ) {
    def artifacts = repoPath.join(",")
    def _outdir = outdir ? outdir : "rtPackages"
    downloadArtifactUsingRepoPath(artifacts, _outdir, verbose)
  }

  def downloadArtifactUsingRepoPath( String repoPath, String outdir = "rtPackages", Boolean verbose = false ) {
    String[] artifacts = repoPath.split(',')
    def _outdir = outdir ? outdir : "rtPackages"
    for ( def artifact in artifacts ) {
      def apiUrl = "storage/${artifact}"

      // def response = steps.httpRequest authentication: steps.GlobalVars.JKN_ARTIFACTORY_USER, url: rtUrl.replace(' ',"%20")
      def response = steps.readJSON text: callApi( apiUrl.replace(' ',"%20"), "", "", "", "", true )
      if( verbose ) { steps.println "response: ${response}" }

      if( response.content.toString().contains('errors') ) { steps.error("${response.content.errors.message} at: ${repoPath}") }

      def artifactName = response.content.path.tokenize('/')[-1]
      def artifactPath = response.content.path.replace('/'+artifactName,'').substring(1)
      def artifactRepo = response.content.repo

      def filesDownloadJson = """
      {
        "files": [{
          "aql": {
            "items.find": {
              "\$and": [{
                "name": { "\$eq": "${artifactName}" },
                "path": { "\$eq": "${artifactPath}" },
                "repo": { "\$eq": "${artifactRepo}" }
              }]
            }
          },
          "target": "${_outdir}\\",
          "flat": "true"
        }]
      }
      """
      if( verbose ) { steps.println "filesDownloadJson: ${filesDownloadJson}" }
      steps.GlobalVars.JKN_ARTIFACTORY_UPLOAD_SERVER.download(filesDownloadJson)
    }
  }

  def downloadArtifactoryFolder(String repoPath, String outdir = "rtFolder") {
    def tempZipFileName = repoPath.replace('\\','/').replace('/','-') + ".zip"
    def rtUrl = "${steps.GlobalVars.JKN_ARTIFACTORY_SOURCE}/api/archive/download/${repoPath}?archiveType=zip"
    def response = steps.httpRequest authentication: steps.GlobalVars.JKN_ARTIFACTORY_USER, url: rtUrl.replace(' ',"%20"), outputFile: tempZipFileName
    steps.unzip zipFile: tempZipFileName,  dir: outdir
    Common.deleteFile( tempZipFileName )
  }

  /* **********************************************
    Retrieves a JSON of Build Artifacts from Artifactory
    Does not support PR- or pre- artifacts
    Will return empty set if no artifact is found meeting criteria input
  */
  def getItemInfo(String packageId, String version, Boolean prerelease = false, Boolean verbose = false, String token = "") {
    // check if version contains hypen, if so, assume it is a prerelease version
    def _prerelease = prerelease
    if( version.contains('-') ) { _prerelease = true }
    
    def _tokenParam = ""
    if (token!="") {_tokenParam = ";token=$token"}

    def response = steps.readJSON text: callPlugin("getItemInfo", "packageId=${packageId};version=${version};prerelease=${_prerelease}${_tokenParam}")
    if( verbose ) { steps.println "response.content:\n" + response.content }
    // if (!response.content) {error "Call to plugin failed to return value!!!! "}
    def build_sourcecode = "Build property does not exists"
    def devops_build_tag = "Build property does not exists"
    def gitCommit = "Build property does not exists"
    def branchName = "Build property does not exists"
    def buildName = ""
    def buildNumber = ""
    def promotion_status = "No promotion status"
    def promotion_comment = "No promotion comment"

    def json = """{"error":"No Items found!"}"""

    if (response.content){
      if( verbose ) { steps.println "response.content.artifacts[0].modules[0].builds.size(): " + response.content.artifacts[0].modules[0].builds.size() }
      response.content.artifacts[0].modules[0].builds.each {
        // if (response.content.name.contains(it."build.number"+'.')) {
        def _buildNumber = it."build.number"
        if( verbose ) { steps.println "Artifact build.number: " + _buildNumber }
        if( response.content.properties.find{ it.key == "nuget.version" } != null ) {
          _buildNumber = response.content.properties.find{ it.key == "nuget.version" }.value
          if( verbose ) { steps.println "This is a nuget package, using nuget.version: " + _buildNumber }
        }
        if (response.content.name.contains(_buildNumber)) {
          if (verbose) { 
            steps.println "it: " + it
            steps.println "it.\"build.created\": " + it."build.created"
            steps.println "it.\"build.name\": " + it."build.name"
            steps.println "it.\"build.number\": " + _buildNumber
            steps.println "it.\"build.properties\": " + it."build.properties"
          }
          buildName = it."build.name"
          buildNumber = _buildNumber
          if( verbose ) { steps.println "it.build.properties=${it."build.properties"}" }
          if( it."build.properties".find{ it."build.property.key" == "buildInfo.env.build_sourcecode" } != null ) {
            build_sourcecode = it."build.properties".find{ it."build.property.key" == "buildInfo.env.build_sourcecode" }."build.property.value"
          }
          if( it."build.properties".find{ it."build.property.key" == "buildInfo.env.devops_build_tag" } != null ) {
            devops_build_tag = it."build.properties".find{ it."build.property.key" == "buildInfo.env.devops_build_tag" }."build.property.value"
          }
          if( it."build.properties".find{ it."build.property.key" == "buildInfo.env.GIT_COMMIT" } != null ) {
            gitCommit = it."build.properties".find{ it."build.property.key" == "buildInfo.env.GIT_COMMIT" }."build.property.value"
          }
          if( it."build.properties".find{ it."build.property.key" == "buildInfo.env.BRANCH_NAME" } != null ) {
            branchName = it."build.properties".find{ it."build.property.key" == "buildInfo.env.BRANCH_NAME" }."build.property.value"
          }
          if( it."build.promotions" != null ) {
            if( it."build.promotions".last()."build.promotion.status" != null ) {
              promotion_status = it."build.promotions".last()."build.promotion.status"
            }
            if( it."build.promotions".last()."build.promotion.comment" != null ) {
              promotion_comment = it."build.promotions".last()."build.promotion.comment"
            }
          }
          json = """
          {
            "name": "${response.content.name}",
            "repo": "${response.content.repo}",
            "path": "${response.content.path}",
            "md5": "${response.content.actual_md5}",
            "sha256": "${response.content.sha256}",
            "build.name": "${buildName}",
            "build.number": "${buildNumber}",
            "build_sourcecode": "${build_sourcecode}",
            "devops_build_tag": "${devops_build_tag}",
            "GIT_COMMIT": "${gitCommit}",
            "BRANCH_NAME": "${branchName}",
            "git_hash_url": "${build_sourcecode.minus(".git")}/commit/${gitCommit}",
            "promotion_status": "${promotion_status}",
            "promotion_comment": "${promotion_comment}"
          }
          """
        }
      }
    }
    steps.println "json: " + json
    return json
  }

  def getItemInfo( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "packageid, version, prerelease, verbose, token"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }

    if( (params.find { it.key == 'packageid' } == null) || (params.find { it.key == 'packageid' } == "") ) {
      steps.error("Parameter 'packageid' is required.")
    }
    def _packageid = params.packageid

    def _version = ""
    if( params.find { it.key == 'version' } != null ) {
      _version = params.version
    }
    
    Boolean _prerelease = false
    if( params.find { it.key == 'prerelease' } != null ) {
      _prerelease = params.prerelease
    }

    Boolean _verbose = false
    if( params.find { it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }

    def _token = ""
    if( params.find { it.key == 'token' } != null ) {
      _token = params.token
    }

    def response = getItemInfo( _packageid, _version, _prerelease, _verbose, _token)
    return response
  }

  def getItemBuildName(String packageId, String version, Boolean prerelease = false, Boolean verbose = false) {
    def buildname = ""
    def response = steps.readJSON text: getItemInfo( packageId, version, prerelease, verbose )
    buildname = response.toString().contains('build.name') ? response.'build.name' : null
    if( buildname == null ) { steps.println "Build name not found for: ${packageId}/${version}" }
    return buildname
  }

  // returns the Artifactory BuildInfo or null
  def getArtifactBuildInfo( String artifact ) {
    def _buildName = null

    def aql = """builds.find({ "module.artifact.item.name": { "\$match": "${artifact}" } })"""
    def response = steps.readJSON text: searchAql( aql )

    // test to ensure only 1 buildinfo is associated with each artifact.
    def resultsSize = response.content.results.size()
    if( resultsSize == 1 ) {
      if( response.content.results[0]."build.name" ) {
        _buildName = response.content.results[0]."build.name"
      }
    }

    if( _buildName == null ) {
      def msg = "Unable to identify Artifactory BuildInfo for artifact: ${artifact} \nPossible reason(s):\n"
      if( resultsSize == 0 ) {
        msg += "No BuildInfo exists for this artifact."
      }
      if( resultsSize > 1 ) {
        msg += "Multiple BuildInfo exists for this artifact. Cannot determine which BuildInfo to return."
      }
      steps.println( msg )
    }
    
    return _buildName
  }

  def getItemBuildSource(String packageId, String version, Boolean prerelease = false, Boolean verbose = false) {
    def buildproperty = ""
    def response = steps.readJSON text: getItemInfo( packageId, version, prerelease, verbose )
    buildproperty = response.toString().contains('build_sourcecode') ? response.build_sourcecode : null
    if( buildproperty == null ) { steps.println "Build source code not found for: ${packageId}/${version}" }
    return buildproperty
  }

  def getItemBuildTag(String packageId, String version, Boolean prerelease = false, Boolean verbose = false) {
    def buildproperty = ""
    def response = steps.readJSON text: getItemInfo( packageId, version, prerelease, verbose )
    buildproperty = response.toString().contains('devops_build_tag') ? response.devops_build_tag : null
    if( buildproperty == null ) { steps.println "Build tag not found for: ${packageId}/${version}" }
    return buildproperty
  }

  def getItemGitCommit(String packageId, String version, Boolean prerelease = false, Boolean verbose = false) {
    def buildproperty = ""
    def response = steps.readJSON text: getItemInfo( packageId, version, prerelease, verbose )
    buildproperty = response.toString().contains('GIT_COMMIT') ? response.GIT_COMMIT : null
    if( buildproperty == null ) { steps.println "Build commit not found for: ${packageId}/${version}" }
    return buildproperty
  }

  /* **********************************************
    checks if Artifactory Build Info exists
    returns true if Artifactory buildInfo exists
    returns false if Artifactory buildInfo does not exist
  */
  def checkBuildInfo(String buildName = steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME, String buildNumber = steps.GlobalVars.JKN_VERSION_FULLVERSION, Boolean quiet = true) {
    def rtUrl = "${steps.GlobalVars.JKN_ARTIFACTORY_SOURCE}/api/build/${buildName}/${buildNumber}"
    def response = ""
    try {
      // we are expecting to get a 404 "not found"
      response = steps.httpRequest authentication: steps.GlobalVars.JKN_ARTIFACTORY_USER, url: rtUrl, validResponseCodes: "100:399,404", quiet: quiet
      if ( response.status == 404 ) {
        return false
      }
      else if ( response.status == 200 ) {
        steps.println "AN ARTIFACTORY BUILDINFO FOR THIS BUILD ALREADY EXISTS!!"
        return true
      }
      else {
        steps.println "Non-404 Response Status: " + response.status
        return true
      }
    }
    catch (e) {
      // all other non-valid returns codes, we print but still continue with the build.
      steps.println response + "\n" + e
      return false;
    }
    return true;
  }

  /* **********************************************
    returns the latest artifactory build number
  */
  def getBuildNumber(String org, String repo, String targetVersion) {
    def response = steps.readJSON text: callPlugin("getBuildNumber", "repo=${org}/${repo};targetversion=${targetVersion}")
    return response.content
  }

 /* **********************************************
    Returns the latest artifactory build number with the given status (sqe, release) for the current build
    If no status is supplied, will find the latest regardles of status.
  */
 def getLatestPromotedBuild( String status="", Boolean verbose = false ) {
  def json = getLatestBuild( steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME, "*", status, verbose )
  return json
} 

  /* **********************************************
    returns the latest artifactory build number with the given status (sqe, release)
    If no status is supplied, will find the latest regardles of status.
    Will also accept a particular build - i.e. "bd-infusion_device-alaris-embsw_12.3.1-post-dev_12.3.1","12.3.1-post-dev.51","release",true
  */
  def getLatestBuild(String buildname=steps.GlobalVars.JKN_ARTIFACTORY_BUILD_NAME, String buildnumber="*", String status="", Boolean verbose = false) {
    def json = ""
    if (!buildname){
      return """{"error":"Build Name must be supplied!"}"""
    }
    def build_sourcecode = "Build property cannot be retrieved"
    def devops_build_tag = "Build property cannot be retrieved"
    def gitCommit = "Build property cannot be retrieved"
    def buildName = ""
    def buildNumber = ""
    def buildCreated = ""
    def buildStarted = ""
    def buildUrl = ""
    def branchName = "Build property cannot be retrieved"
    def debug= verbose==true ? "debug=true;": "" 
    def artifacts = []
    def artifactString = ""
    def artifactList = ""
    def artifact =""
    def _repo = ""
    def _path = ""
    def _name = ""
    def _md5 = ""
    def _sha256 = ""

    def holder = callPlugin("getlatestbuild", "name=${buildname};number=${buildnumber};status=${status};${debug}")
    def response = steps.readJSON text: holder 
    if (response.content["error"]){
      return """{"error":"${response.content["error"]}"}"""
    } else {
      buildName = response.content["build.name"]
      buildNumber = response.content["build.number"]
      buildCreated = response.content["build.created"]
      buildStarted = response.content["build.started"]
      buildUrl = response.content["build.url"]
      if (verbose) {steps.println "LatestBuildInfo is buildName=$buildName, buildNumber=$buildNumber, buildCreated=$buildCreated"   }


      if( response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.build_sourcecode" } != null ) {
        build_sourcecode = response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.build_sourcecode" }."build.property.value"
      }
      if( response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.devops_build_tag" } != null ) {
        devops_build_tag = response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.devops_build_tag" }."build.property.value"
      }
      if( response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.GIT_COMMIT" } != null ) {
        gitCommit = response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.GIT_COMMIT" }."build.property.value"
      }
      if( response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.BRANCH_NAME" } != null ) {
        branchName = response.content["build.properties"].find{ it."build.property.key" == "buildInfo.env.BRANCH_NAME" }."build.property.value"
      }

      if (response.content["modules"]){
        artifactList = response.content["modules"]["artifacts"]["items"]
        if (verbose){
          steps.println "artifactList: " + artifactList 
        }

        artifact =""
        if (artifactList){  
          artifactList[0][0].each { 
            _repo = it.repo
            _path = it.path
            _name = it.name
            _md5 = it.actual_md5
            _sha256 = it.sha256

          artifact = """
          {
            "name":"${_name}",
            "repo":"${_repo}",
            "path":"${_path}",
            "md5":"${_md5}",
            "sha256":"${_sha256}",
          }
          """
          artifactString =  artifactString !=""? artifactString + ',' + artifact: artifact 
          } 
        }

        if (verbose){
        steps.println "artifactString=" + artifactString
        }
      }
      json = """
      {
        "build.name": "${buildName}",
        "build.number": "${buildNumber}",
        "buildCreated": "${buildCreated}",
        "buildStarted": "${buildStarted}",
        "buildUrl": "${buildUrl}",  
        "build_sourcecode": "${build_sourcecode}",
        "devops_build_tag": "${devops_build_tag}",
        "GIT_COMMIT": "${gitCommit}",
        "BRANCH_NAME": "${branchName}",
        "artifacts": [${artifactString}]
      }
      """
    }
    if (verbose) {steps.println "json: $json"}
    return json
  }


  /* **********************************************
    returns true if the Jenkins Version is greater than the latest targetversion in Artifactory
    returns false if the Jenkins Version is less than or equal to the latest targetversion in Artifactory.
  */
  def compareJenkinsVersionToArtifactoryVersion(String org, String repo, String targetVersion, String fullversion = steps.GlobalVars.JKN_VERSION_FULLVERSION) {
    def response = steps.readJSON text: callPlugin("getBuildNumber", "repo=${org}/${repo};targetversion=${targetVersion};fullversion=${fullversion}")
    return response.content
  }

    /* **********************************************
    calls Artifactory plugin addDockerImageInfo
    Returns JSON with information for updated build. This does not support pre-release builds.
    Artifactory plugin to add docker images using the sha1 for manifest.json of docker repo. 
  */
  def addDockerImageToBuildInfo(String buildInfoName, String buildInfoNumber, String shaType, String manifestJsonShas, Boolean debug = false ) {
    def response = steps.readJSON text: callPlugin("addockerimage", "buildname=${buildInfoName};buildnumber=${buildInfoNumber};type=${shaType};artifacts=${manifestJsonShas};debug=${debug}")
    return response.content
  }

  /* **********************************************
    calls Artifactory plugin getDockerImageInfo
    returns build info in a json object
    dockerRepoName: the name of the docker image
    debug: set to true to debug the logback from Artifactory
  */
  def getDockerImageInfo(String dockerRepo, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getDockerImageInfo", "dockerRepoName=${dockerRepo};version=${version};debug=${debug}")
    return response.content
  }

  // Returns the build label of the Docker Image
  def getDockerImageLabel(String dockerRepo, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getDockerImageInfo", "dockerRepoName=${dockerRepo};version=${version};debug=${debug}")
    if(response.content.properties.find{ it.key == "docker.label.label" } != null) {
      return response.content.properties.find{ it.key == "docker.label.label" }.value
    }
    return ""
  }

  // Returns the Git Commit of the Docker Image source code
  def getDockerImageCommit(String dockerRepo, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getDockerImageInfo", "dockerRepoName=${dockerRepo};version=${version};debug=${debug}")
    if(response.content.properties.find{ it.key == "docker.label.vcs.revision" } != null) {
      return response.content.properties.find{ it.key == "docker.label.vcs.revision" }.value
    }
    return ""
  }

  // Returns the version number of the Docker Image
  def getDockerImageManifest(String dockerRepo, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getDockerImageInfo", "dockerRepoName=${dockerRepo};version=${version};debug=${debug}")
    if(response.content.properties.find{ it.key == "docker.manifest" } != null) {
      return response.content.properties.find{ it.key == "docker.manifest" }.value
    }
    return ""
  }

  // Returns the sha256 digest of the Docker Image
  def getDockerImageManifestDigest(String dockerRepo, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getDockerImageInfo", "dockerRepoName=${dockerRepo};version=${version};debug=${debug}")
    if(response.content.properties.find{ it.key == "docker.manifest.digest" } != null) {
      return response.content.properties.find{ it.key == "docker.manifest.digest" }.value
    }
    return ""
  }

  // Returns the Artifactory Build URL of the Docker Image
  def getDockerImageBuildUrl(String dockerRepo, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getDockerImageInfo", "dockerRepoName=${dockerRepo};version=${version};debug=${debug}")
    if(response.content.properties.find{ it.key == "docker.label.devops_build_url" } != null) {
      return response.content.properties.find{ it.key == "docker.label.devops_build_url" }.value
    }
    return ""
  }


  /* **********************************************
    calls Artifactory plugin getHelmChartInfo
    returns build info in a json object
    getHelmChartInfo: name of the helm chart
    debug: set to true to debug the logback from Artifactory
  */
  def getHelmChartInfo(String helmChartName, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getHelmChartInfo", "helmChartName=${helmChartName};version=${version};debug=${debug}")
    return response.content
  }

  // Returns the Helm Chart AppVersion
  def getHelmChartAppVersion(String helmChartName, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getHelmChartInfo", "helmChartName=${helmChartName};version=${version};debug=${debug}")
    if(response.content.properties.find{ it.key == "chart.appVersion" } != null) {
      return response.content.properties.find{ it.key == "chart.appVersion" }.value
    }
    return ""
  }

  // Returns the Helm Chart Version
  def getHelmChartVersion(String helmChartName, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getHelmChartInfo", "helmChartName=${helmChartName};version=${version};debug=${debug}")
    if(response.content.properties.find{ it.key == "chart.version" } != null) {
      return response.content.properties.find{ it.key == "chart.version" }.value
    }
    return ""
  }

  // Returns the Helm Chart full name
  def getHelmChartFullName(String helmChartName, String version, Boolean debug = false) {
    def response = steps.readJSON text: callPlugin("getHelmChartInfo", "helmChartName=${helmChartName};version=${version};debug=${debug}")
    return response.content.name
  }


  private def formatPackageRestoreLog(String packageList = steps.GlobalVars.JKN_PACKAGE_RESTORE_LOG) {
    if (packageList) {
      def pkgs = packageList.trim().split('\n') as List
      def pkgJson = ""
      for (def pkg in pkgs) {
        def finder = (pkg.trim() =~ /^([^\s]+)\s+(.*)$/)
        if(finder.matches()) {
          def addPkg = "{\"${finder.group(1)}\": \"${finder.group(2)}\"}"
          if (!pkgJson.contains(addPkg)) {
            pkgJson = pkgJson ? pkgJson + "," + addPkg : addPkg
          }
        }
        finder = null
      }
      pkgJson = "{\"packages\": [${pkgJson}] }"
      return pkgJson
    }
  }

  private def cleanUploadArtifactsDir(String pathGlob) {
    def movedArtifactsDir = "devops-output"
    String[] files = steps.findFiles(glob: pathGlob)
    if (files.size() > 0) {
      for (def file in files) {
        Common.copyFile(file,movedArtifactsDir)
        Common.deleteFile(file)
      }
    }
  }

  private def verifyRepository( String repoName, Boolean verbose = false ) {
    def rtUrl = "${steps.GlobalVars.JKN_ARTIFACTORY_SOURCE}/api/repositories/${repoName}"
    def response = steps.httpRequest url: rtUrl.replace(' ',"%20"), quiet: verbose, validResponseCodes: "100:399,400"
    if ( response.status == 400 ) {
      steps.error( "The defined Artifactory Repository: ${repoName}, does not exists, please verify spelling (case sensitive)." )
    }
  }

  // Artifactory api/search/aql
  def searchAql( String body, String accepttype = "APPLICATION_JSON", String contenttype = "TEXT_PLAIN", Boolean verbose = false ) {
    def api = "search/aql"
    
    // search/aql is a POST
    def response = callApi( api, body, "POST", accepttype, contenttype, verbose )
    if( verbose ) {
      steps.println response
    }
    return response
  }

  //SEARCHES section for Jfrog API 
  def searchPost(String body, String rtsource = "", Boolean verbose = false) {
      def _rtsource = rtsource ? "https://${rtsource}/artifactory" : steps.GlobalVars.JKN_ARTIFACTORY_SOURCE
      def response = steps.httpRequest(
        url:"${_rtsource}/api/search/aql", 
        authentication: steps.GlobalVars.JKN_ARTIFACTORY_USER,
        httpMode: 'POST', 
        acceptType: 'APPLICATION_JSON', 
        contentType: 'TEXT_PLAIN', 
        requestBody: body, 
        validResponseCodes: '100:500',
        timeout: 2400
    )
    if (verbose){
      steps.println response }
    return response
  }

  // pass in the json object from RT.getItemInfo
  def addToArtifactJson( jsonObj, String artifactvariable = "" ) {
    def _jsonObj = jsonObj.toString()
    def _artifactvariable = artifactvariable
    // individual json
    if( !_artifactvariable.contains(_jsonObj) ) {
      _artifactvariable = _artifactvariable ? _artifactvariable + ',' + _jsonObj : _jsonObj
    }
    
    // global json 
    if( !steps.GlobalVars.JKN_ARTIFACTS.contains(_jsonObj) ) {
      steps.GlobalVars.JKN_ARTIFACTS = steps.GlobalVars.JKN_ARTIFACTS ? steps.GlobalVars.JKN_ARTIFACTS + ',' + _jsonObj : _jsonObj
    }

    return _artifactvariable
  }

  // creates the artifact.json file from steps.GlobalVars.JKN_ARTIFACTS
  def createArtifactJsonFile( String filename = "BuildResults\\artifacts.json", String artifactsJson = steps.GlobalVars.JKN_ARTIFACTS ) {
    def contents = steps.readJSON text: "{ \"artifacts\": [${artifactsJson}]}"
    steps.writeJSON(file: filename, json: contents, pretty: 4)
  }

  // creates the buildsincluded file from steps.GlobalVars.JKN_ARTIFACTS
  def createBuildsIncludedJsonFile( String filename = "BuildResults\\buildsincluded.json", String artifactsJson = steps.GlobalVars.JKN_ARTIFACTS ) {
    def includedBuilds = ""
    steps.readJSON(text: "[${artifactsJson}]").each {
      def buildJson = """{"build.name":"${it."build.name"}","build.number":"${it."build.number"}","devops_build_tag":"${it.devops_build_tag}","GIT_COMMIT":"${it.GIT_COMMIT}","build_sourcecode":"${it.build_sourcecode}","BRANCH_NAME":"${it.BRANCH_NAME}","promotion_status":"${it.promotion_status}"}"""
      includedBuilds = (includedBuilds.contains(it."build.name") && includedBuilds.contains(it."build.number")) ? includedBuilds : includedBuilds ? includedBuilds + ',' + buildJson : buildJson
    }
    def includedBuildsContent = steps.readJSON text: "{ \"builds\": [${includedBuilds}]}"
    steps.writeJSON(file: filename, json: includedBuildsContent, pretty: 4)
  }

  def setDevopsEnclosedPkgsEnv(String pkgEnv = "") {
    if( pkgEnv ) {
      steps.env.devops_enclosed_packages = steps.env.devops_enclosed_packages ? (steps.env.devops_enclosed_packages.contains(pkgEnv) ? steps.env.devops_enclosed_packages : steps.env.devops_enclosed_packages + ',' + pkgEnv) : pkgEnv
      addCustomProperties("{\"devops_enclosed_packages\":\"$pkgEnv\"}")
    }
  }

  def getBuildInfoPromotionStatus( String buildname, String buildnumber, Boolean verbose = false ) {
    def _buildInfo = "{\"comment\": \"No build promotions found.\"}"

    def aql = """builds.find({"\$and": [{"name":{"\$match":"${buildname}"}},{"number":{"\$match":"${buildnumber}"}}]}).include("promotion.created","promotion.status")"""
    def response = steps.readJSON text: searchAql( aql )

    if( verbose ) { steps.println "response: " + response }

    if( response.content.results[0]."build.promotions" ) {
      _buildInfo = response.content.results[0]."build.promotions".toString()
    }

    return _buildInfo
  }

  // by default, this closure promotes the current running build if no buildname/buildnumber are passed in.
  def promote( Closure body ) {
    def params = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = params
    body()
    def supportedParameters = "buildname, buildnumber, status, dryrun, requester, recipients, verbose"
    params.each {
      if( !supportedParameters.contains(it.key) ) { steps.error("Unsupported parameter found!: ${it.key}\nSupported parameters: ${supportedParameters}") }
    }

    def _buildname = steps.GlobalVars.JKN_BUILD_NAME
    if( params.find { it.key == 'buildname' } != null ) {
      _buildname = params.buildname
    }
    // throw error when trying to promote PR/prerelease builds
    if( _buildname.toLowerCase().contains("_pr-") || _buildname.toLowerCase().contains("_pre-") ) {
      steps.error( "Buildname: ${_buildname}, cannot be promoted. This is a PR/prerelease build." )
    }
    def _buildnumber = steps.GlobalVars.JKN_VERSION_FULLVERSION
    if( params.find { it.key == 'buildnumber' } != null ) {
      _buildnumber = params.buildnumber
    }
    def _status = "sqe"
    if( params.find { it.key == 'status' } != null ) {
      _status = params.status.toLowerCase()
    }
    if( _status != "sqe" ) {
      steps.error( "Jenkins pipeline only supports promotion status: \"sqe\", please update the promotion status to sqe." )
    }
    def _dryrun = false
    if( params.find { it.key == 'dryrun' } != null ) {
      _dryrun = params.dryrun
    }
    Boolean _verbose = false
    if( params.find { it.key == 'verbose' } != null ) {
      _verbose = params.verbose
    }
    // if requester is "", assume this is a pipeline promotion: has to be email address
    def _requester = ""
    if( params.find { it.key == 'requester' } != null ) {
      _requester = params.requester
    }
    if( _requester == "" ) {
      switch( JENKINS.getBuildCauseTrigger() ) {
        case "userid":
          // User trigger "Build Now" -- get the user email who triggered the build
          _requester = JENKINS.getBuildCauseUserEmail()
          break
        case "replay":
          // User trigger "Replay" -- get the user email who replayed the build
          _requester = JENKINS.getBuildCauseUserEmail()
          break
        case "branchevent":
          // A push event to Jenkins -- get the Git Commit User Email
          _requester = Git.getCommitEmail()
          break
        case "buildupstream":
          // Trigger from an upstream build job -- get the Git Commit User Email
          _requester = Git.getCommitEmail()
          break
        case "timertrigger":
          // A Cron job trigger -- get the Git Commit User Email
          _requester = Git.getCommitEmail()
          break
        default:
          _requester = "sa-devops-jenkins@bd.com"
          break
      }
    }
    def _recipients = ""
    if( params.find { it.key == 'recipients' } != null ) {
      _recipients = params.recipients
    }
    if( _recipients == "" ) {
      _recipients = _requester
    }
    else {
      _recipients = "${_recipients},${_requester}"
    }

    def _description = "Promoted%20by%20${_requester}"
    if( _requester == "sa-devops-jenkins@bd.com" ) {
      _description = "Jenkins%20pipeline%20promotion."
    }

    def _args = "buildname=${_buildname};buildnumber=${_buildnumber};requestor=${_requester};status=${_status};description=${_description};recipients=${_recipients}"

    if( _dryrun ) {
      _args = "${_args};dryRun=${_dryrun}"
      steps.println "DRYRUN IS SET TO TRUE. THIS WILL ONLY SIMULATE A PROMOTION!!"
    }
    
    def response = steps.readJSON text: callPlugin( "promote", _args, "POST", _verbose )
    
    // parse response.content for errors. 
    if( response.status == "200" ) {
      steps.println _dryrun ? "DRYRUN IS SET TO TRUE.\nNO PROMOTION EXECUTED, SIMULATED RESPONSE:\n${response.content}" : 
                              "Build Promoted to ${_status}: ${_buildname} ${_buildnumber}\n${response.content}"
    }
    else {
      def _errors = ""
      if( response.content["errors"] ) {
        _errors = response.content["errors"].get(0).message
      }
      if( _errors.contains("already promoted") ) {
        // do not error if the build is already promoted
        steps.println _errors
      }
      else if( _errors.contains("Unable to find Destination Path") ) {
        def msg = "Unable to promote build ${_buildname} ${_buildnumber} to ${_status}\nERROR: ${_errors}\n*NOTE: Artifactory may not support this promotion status and/or repository type. See DevOps if you believe the promotion status or repository should exist."
        steps.error( msg )
      }
      else if( _errors.contains("is not allowed to promote to status of") ) {
        def msg = "Unable to promote build ${_buildname} ${_buildnumber} to ${_status}\nERROR: ${_errors}\n*NOTE: Artifactory may not support this promotion status and/or repository type. See DevOps if you believe the promotion status or repository should exist."
        steps.error( msg )
      }
      else if( _errors.contains("Unable to find target build") ) {
        def msg = "Unable to promote build ${_buildname} ${_buildnumber} to ${_status}\nERROR: ${_errors}\n*NOTE: This buildinfo does not exists in Artifactory. Please verify the build you are trying to promote."
        steps.error( msg )
      }
      else {
        def msg = "Build promotion failed for: ${_buildname} ${_buildnumber} to ${_status}\nERROR: ${_errors}\n*NOTE: See DevOps for further support."
        steps.error( msg )
      }
    }
  }

  // Finds artifacts in a build, based on either artifact name or build name/number
  // If artifact is defined and allartifacts=false, then returns only information on artifact specified
  def getBuildArtifactInfo( Map inputArgs=[:], Boolean verbose = false ) {
    def _artifact = inputArgs.containsKey("artifact")? inputArgs["artifact"]:""
    def _buildName = inputArgs.containsKey("buildname")? inputArgs["buildname"]:""
    def _buildNumber = inputArgs.containsKey("buildnumber")? inputArgs["buildnumber"]:""
    def _allArtifacts = inputArgs.containsKey("allartifacts")? new Boolean(inputArgs["allartifacts"]) : true

    if( !_artifact && !_buildName && !_buildNumber ) {
      steps.error("'artifact' and/or (`buildname` and 'buildnumber') must be supplied!!")
    }
    
    if( verbose ) { steps.println "artifact=$_artifact, buildName=$_buildName, buildNumber=$_buildNumber, allArtifacts=$_allArtifacts" }

    def response = steps.readJSON text: callPlugin("getbuildartifactsinfo", "artifact=${_artifact};buildname=${_buildName};buildnumber=${_buildNumber};allartifacts=${_allArtifacts}")
    if( verbose ) { steps.println "response: \n" + response }

    // if (!response.content) { steps.error "Call to plugin failed to return value!!!! "}
    
    return response
  }

}

