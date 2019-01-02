import groovy.json.JsonSlurper
/**
 *  Plex Communicator
 *
 *  Copyright 2018 Jake Tebbett
 *	Credit To: Christian Hjelseth, iBeech & Ph4r as snippets of code taken and amended from their apps
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 * VERSION CONTROL
 * ###############
 *
 *  v0.1 - Test Release
 *
 */

definition(
    name: "Emby to Vudu",
    namespace: "Keo",
    author: "Joe Rosiak",
    description: "Allows Emby to launch vudu movies using roku",
    category: "My Apps",
    iconUrl: "https://github.com/XXKeoXX/emby/raw/master/icon.png",
    iconX2Url: "https://github.com/XXKeoXX/emby/raw/master/icon.png",
    iconX3Url: "https://github.com/XXKeoXX/emby/raw/master/icon.png",
    oauth: [displayName: "EmbyServer", displayLink: ""])


def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(location, null, response, [filterEvents:false])
}

preferences {
	page name: "mainMenu"
    page name: "noAuthPage"
    page name: "authPage"
    page name: "authPage2"
//    page name: "clientPage"
//    page name: "clearClients"
    page name: "mainPage"
    page name: "ApiSettings"
}

mappings {
  path("/statechanged/:command") 	{ action: [GET: "plexExeHandler"] }
  path("/p2stset") 					{ action: [GET: "p2stset"]   }
  path("/pwhset") 					{ action: [GET: "pwhset"]   }
  path("/pwh") 						{ action: [POST: "plexWebHookHandler"] }
}


/***********************************************************
** Main Pages
************************************************************/

def mainMenu() {
	// Get ST Token
	log.trace "Hit Main Menu"
    try { if (!state.accessToken) {createAccessToken()} }
    catch (Exception e) {
    	log.info "Unable to create access token, OAuth has probably not been enabled in IDE: $e"
        return noAuthPage()
    }

	if (state?.authenticationToken) { return mainPage() }
    else { return ApiSettings() }
}

def noAuthPage() {
	log.trace "Hit noAuthPage"
	return dynamicPage(name: "noAuthPage", uninstall: true, install: true) {
		section("*Error* You have not enabled OAuth when installing the app code, please enable OAuth")
    }
}

def mainPage() {
	log.trace "Hit Main Page"
	return dynamicPage(name: "mainPage", uninstall: true, install: true) {
		section("Main Menu") {
        	href(name: "ApiSettings", title: "Connection Methods", required: false, page: "ApiSettings", description: "Select your method for connecting to Emby")
    	}
    }
}

def ApiSettings() {
	log.trace "Hit API Settings Page"
    dynamicPage(name: "ApiSettings", title: "Select Your Connection Method", install: true, uninstall: true	) {
		section("Emby Information"){
    		input("embyIpAdd", "text", title: "IP Address", description: "Your Emby Server IP Address", required: true)
        	input("embyApiKey", "text", title: "API Key", description: "Your Emby API Key", required: true)
			input("embyApiPort", "text", title: "Emby Port", description: "Your Emby api listen port", required: true, defaultValue: "8096")
			input("rokuListenPort", "text", title: "Roku Port", description: "Your Roku listen port", required: true, defaultValue: "8060")
		}
        section("Webhooks - Emby service required") {
        	paragraph("Note: You will need an active Emby Subscription to use this")
        	href url: "${getLocalApiServerUrl()}/${app.id}/pwhset?access_token=${state.accessToken}", style:"embedded", required:false, title:"Emby Webhooks Settings", description: ""  		
        }
		section("NOTE: The settings above have also been sent to Live Logging, for easy access from a computer."){}
        	log.debug(
        		"\n ## URL FOR USE IN EMBY WEBHOOKS ##\n${getFullLocalApiServerUrl()}/pwh?access_token=${state.accessToken}"+
        		"<!ENTITY accessToken '${state.accessToken}'>\n"+
				"<!ENTITY appId '${app.id}'>\n")
	}
}

def pwhset() {
	log.trace "Running pwhset"
    def html = """<html><head><title>Plex2SmartThings Settings</title></head><body><h1>
        ${getFullLocalApiServerUrl()}/pwh?access_token=${state.accessToken}<br />
    </h1></body></html>"""
    render contentType: "text/html", data: html, status: 200
}

/*
def p2stset() {
	log.trace "Running p2stset"
    def html = """
    <!DOCTYPE html>
    <html><head><title>Plex Webhooks Settings</title></head><body><p>
        &lt;!ENTITY accessToken '${state.accessToken}'><br />
        &lt;!ENTITY appId '${app.id}'><br />
        &lt;!ENTITY ide '${getFullLocalApiServerUrl()}'><br />
        &lt;!ENTITY plexStatusUrl 'http://${settings.plexServerIP}:32400/status/sessions?X-Plex-Token=${state.authenticationToken}'>
    </p></body></html>"""
    render contentType: "text/html", data: html, status: 200
}
*/


/***********************************************************
** INPUT HANDLERS
************************************************************/
def plexWebHookHandler(){
	def payload = request.body
	log.debug "Webhook Received with payload - $payload"
    def jsonSlurper = new JsonSlurper()
	def plexJSON = jsonSlurper.parseText(payload)
	def mediaType = plexJSON.Metadata.parentTitle
    def movieName = plexJSON.Metadata.title
	def clientUuid = plexJSON.Player.uuid
	def movieId = plexJSON.Metadata.guid
    def status = plexJSON.event
    def plexEvent = [:] << [ movieName: "$movieName", type: "$mediaType", status: "$status", movieId: "$movieId" ]
    //log.debug plexEvent
	def contentId = makeJSONMovieInfoRequest(movieId)
	//log.debug "contentId = $contentId"
	def clientIp = getDeviceIp(clientUuid)
	launchPlayer(contentId,clientIp)
}

/***********************************************************
** GET API DATA
************************************************************/

def makeJSONMovieInfoRequest(movieId) {
	log.trace "Getting Movie MetaData"
    def paramsg = [
		uri: "http://${settings.embyIpAdd}:${settings.embyApiPort}/emby/Items?Fields=Tags&Ids=$movieId&api_key=${settings.embyApiKey}",
		contentType: 'application/json'
	]
	httpGet(paramsg) { resp ->
        def myTags = resp.data.Items[0].Tags
		log.debug "myTags = ${myTags}"
		def myVuduTag = myTags.find{ name -> name =~ /^vudu-.*/ }
		log.debug myVuduTag
		return myVuduTag.split('-').last()
	}
}

def getDeviceIp(clientUuid) {
	log.trace "Getting client IP"
	def paramid = [
		uri: "http://${settings.embyIpAdd}:${settings.embyApiPort}/emby/Sessions?DeviceId=$clientUuid&api_key=${settings.embyApiKey}",
		contentType: 'application/json'
	]
	httpGet(paramid) { resp ->
		return resp.data[0].RemoteEndPoint
		log.debug clientIp
	}
}


/***********************************************************
** Launch Vudu Movie
************************************************************/
def launchPlayer(contentId,clientIp) {
	log.debug "Launching Vudu app on roku using http://$clientIp:${settings.rokuListenPort}/launch/13842?contentId=$contentId"
    def paramsp = [
		uri: "http://$clientIp:${settings.rokuListenPort}/launch/13842?contentId=$contentId",
		contentType: 'application/json'
	]
	httpPost(paramsp) { resp ->
		log.debug resp.data }
}
