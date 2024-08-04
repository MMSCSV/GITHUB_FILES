package io.bddevops.npm

import io.bddevops.artifactory.RT
import io.bddevops.common.Common

class Npm implements Serializable {
  def steps
  def RT
  def Common

  Npm(steps) {
    this.steps = steps

    RT = new RT(steps)
    Common = new Common(steps)
  }

  def execute(String args) {
    Common.genericScriptCmd("npm ${args}")
  }

  def readPackageJson(String file = 'package.json') {
    if(!Common.checkFileExists(file)) {
      steps.currentBuild.result = "FAILURE"
      steps.error("UNABLE TO FIND FILE: \"${file}\", THIS IS A SOURCE CODE ISSUE, PLEASE ENSURE THE FILE EXISTS")
    }
    def props = steps.readJSON file: file
    steps.GlobalVars.JKN_NPM_NAME = props['name']
    if ( steps.GlobalVars.JKN_NPM_NAME.contains('/') ) {
      def tokens = steps.GlobalVars.JKN_NPM_NAME.tokenize('/')
      steps.GlobalVars.JKN_NPM_SCOPE = tokens[0]
      steps.GlobalVars.JKN_NPM_NAME = tokens[1]
      steps.println """
============================================================
NPM SCOPE: ${steps.GlobalVars.JKN_NPM_SCOPE}
NPM PACKAGE NAME: ${steps.GlobalVars.JKN_NPM_NAME}
============================================================  
"""
    }
    def depValues = props['dependencies'].toString()
    if (depValues){
      steps.GlobalVars.JKN_NPM_DEPENDENCIES = depValues.replaceAll('}','')
      steps.GlobalVars.JKN_NPM_DEPENDENCIES = steps.GlobalVars.JKN_NPM_DEPENDENCIES.replaceAll('\\{','')
      steps.GlobalVars.JKN_NPM_DEPENDENCIES = steps.GlobalVars.JKN_NPM_DEPENDENCIES.replaceAll('"','')
      steps.println """
        JKN_NPM_DEPENDENCIES.final = ${steps.GlobalVars.JKN_NPM_DEPENDENCIES}
      """
    }
  }

  def version(String filePath = 'package.json', String version = steps.GlobalVars.JKN_VERSION_FULLVERSION, String args = "") {
    def rootDir = ""
    filePath = filePath.replace('\\','/')
    if (filePath.contains('/')) {
      rootDir = filePath.replace(filePath.tokenize('/').last(),'')
    }
    def strVersion = version ? version : steps.GlobalVars.JKN_VERSION_FULLVERSION
    def versionArgs = args ? "version ${strVersion} ${args} --allow-same-version true --git-tag-version false --commit-hooks false" : "version ${strVersion} --allow-same-version true --git-tag-version false --commit-hooks false"
    if(rootDir) {
      steps.dir(rootDir) {
        execute(versionArgs)
      }
    }
    else {
      execute(versionArgs)
    }
  }

  def install(String args = "") {
    def packageLockFile = steps.findFiles(glob: '**/package-lock.json')
    def buildVerb = "install"
    if(packageLockFile.size() > 0) {
      buildVerb = "ci"
    }
    def installArgs = (buildVerb=="install") ? "${buildVerb} ${args} --quiet --no-progress" : buildVerb
    execute(installArgs)
  }

  def run(String args) {
    execute("run ${args}")
  }

  def lint(String args = "") {
    def lintArgs = args ? "lint ${args}" : "lint"
    run(lintArgs)
  }

  def pack(String args = "") {
    def packArgs = args ? "pack ${args}" : "pack"
    execute(packArgs)
  }

  def test(String args = "") {
    def packArgs = args ? "test ${args}" : "test"
    execute(packArgs)
  }

  // uploadSpecNpm("./*.tgz","${steps.GlobalVars.JKN_GIT_ORG.toLowerCase()}-npm", "(*)-(${steps.GlobalVars.JKN_VERSION_FULLVERSION})", false) {
  def publish(String pathGlob = "./*.tgz", String rtRepo = "${steps.GlobalVars.JKN_GIT_ORG.toLowerCase()}-npm", String rtGrouping = "(*)-(${steps.GlobalVars.JKN_VERSION_FULLVERSION})", Boolean verbose = false) {
    RT.uploadSpecNpm(pathGlob,rtRepo,rtGrouping,verbose)
  }

  def stages(String file = 'package.json', String targetVersion = steps.GlobalVars.JKN_VERSION_FULLVERSION, Boolean verbose = false, Closure body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config

    steps.stage('Update-Version') {
      readPackageJson(file)
      version(file)
    }

    body()

    config.each { key, value ->
      def stageName = key.capitalize()
      steps.stage (stageName) {
        Common.genericScriptCmd(value)
      }
    }

    steps.stage('Publish-Npm') {
      publish()
    }
  }

  def pipeline(String file = 'package.json', String targetVersion = steps.GlobalVars.JKN_VERSION_FULLVERSION, Boolean verbose = false, body) {
    steps.stage('Update-Version') {
      readPackageJson(file)
      version(file)
    }

    body.call()

    steps.stage('Publish-Npm') {
      RT.uploadSpecNpm()
    }
  }

}
