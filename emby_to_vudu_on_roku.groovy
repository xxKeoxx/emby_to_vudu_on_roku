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
    name: "Plex Communicator",
    namespace: "jebbett",
    author: "Jake Tebbett",
    description: "Allow SmartThings and Plex to Communicate",
    category: "My Apps",
    iconUrl: "https://github.com/jebbett/Plex-Communicator/raw/master/icon.png",
    iconX2Url: "https://github.com/jebbett/Plex-Communicator/raw/master/icon.png",
    iconX3Url: "https://github.com/jebbett/Plex-Communicator/raw/master/icon.png",
    oauth: [displayName: "PlexServer", displayLink: ""])


def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
	// sub to plex now playing response
    subscribe(location, null, response, [filterEvents:false])
    // Add New Devices
    //def storedDevices = state.plexClients
    //settings.devices.each {deviceId ->
    //    try {
    //        def existingDevice = getChildDevice(deviceId)
    //        if(!existingDevice) {
    //            def theHub = location.hubs[0]
    //        	log.warn "${deviceId} and ${theHub}"
    //            //def childDevice = addChildDevice("jebbett", "Plex Communicator Device", deviceId, theHub.id, [name: "${deviceId}", isComponent: false])
    //            def childDevice = addChildDevice("jebbett", "Plex Communicator Device", deviceId, theHub.id, [name: deviceId, label: storedDevices."$deviceId".name, completedSetup: false])
    //        }
    //    } catch (e) { log.error "Error creating device: ${e}" }
    //}
    // Clean up child devices
    //if(settings?.devices) {
    //	getChildDevices().each { if(settings.devices.contains(it.deviceNetworkId)){}else{deleteChildDevice("${it.deviceNetworkId}")} }
    //}else{
    //	getChildDevices().each { deleteChildDevice("${it.deviceNetworkId}") }
    //}
    // Just in case plexPoller has gasped it's last breath (in case it's used)
    if(settings?.stPoller){runEvery3Hours(plexPoller)}
}

preferences {
	page name: "mainMenu"
    page name: "noAuthPage"
    page name: "authPage"
    page name: "authPage2"
    page name: "clientPage"
    page name: "clearClients"
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
    try { if (!state.accessToken) {createAccessToken()} }
    catch (Exception e) {
    	log.info "Unable to create access token, OAuth has probably not been enabled in IDE: $e"
        return noAuthPage()
    }

	if (state?.authenticationToken) { return mainPage() }
    else { return authPage() }
}

def noAuthPage() {
	
	return dynamicPage(name: "noAuthPage", uninstall: true, install: true) {
		section("*Error* You have not enabled OAuth when installing the app code, please enable OAuth")
    }
}

def mainPage() {
	return dynamicPage(name: "mainPage", uninstall: true, install: true) {
		section("Main Menu") {
        	href "clientPage", title:"Select Your Devices", description: "Select the devices you want to monitor" 
            href "authPage", title:"Plex Account Details", description: "Update Plex Account Details"
        	href(name: "ApiSettings", title: "Connection Methods", required: false, page: "ApiSettings", description: "Select your method for connecting to Plex")
    	}
        section("If you want to control lighting scenes then the 'MediaScene' SmartApp is ideal for this purpose"){}
        section("This app is developed by jebbett, additional credit goes to Christian H (Plex2SmartThings), iBeech (Plex Home Theatre) & Ph4r (Improved Plex control)."){}
    }
}

def ApiSettings() {
    dynamicPage(name: "ApiSettings", title: "Select Your Connection Method", install: false, uninstall: false) {      
        section("1. Plex Webhooks - Plex Pass Only (Best)") {
        	paragraph("Plex Webhooks is the best method for connecting Plex to SmartThings, however you will need an active Plex Pass Subscription to use it")
        	href url: "${getLocalApiServerUrl()}/${app.id}/pwhset?access_token=${state.accessToken}", style:"embedded", required:false, title:"Plex Webhooks Settings", description: ""  		
        }
        section("2. Plex2SmartThings Program") {
        	paragraph("This involves running a program on an always on computer")
            href url: "${getLocalApiServerUrl()}/${app.id}/p2stset?access_token=${state.accessToken}", style:"embedded", required:false, title:"Plex2SmartThings Program Settings", description: ""    		
        }
        section("3. SmartThings Polling *Not Recommended*") {
        	paragraph("SmartThings will poll every 10 seconds and request the status from Plex, however this method is unreliable and puts increased load on SmartThings and your network (Don't complain to me that it stops working occasionally)")
            input "stPoller", "bool", title: "Enable - At your own risk", defaultValue:false, submitOnChange: true
        }
        if(settings?.stPoller){plexPoller()}
        
        
        section("NOTE: The settings for both of the above have also been sent to Live Logging, ffor easy access from a computer."){}
        log.debug(
        		"\n ## URL FOR USE IN PLEX WEBHOOKS ##\n${getFullLocalApiServerUrl()}/pwh?access_token=${state.accessToken}"+
        		"\n\n ## SETTINGS TO USE IN THE EXE ##\n"+
        		"<!ENTITY accessToken '${state.accessToken}'>\n"+
				"<!ENTITY appId '${app.id}'>\n"+
				"<!ENTITY ide '${getFullLocalApiServerUrl()}'>\n"+
				"<!ENTITY plexStatusUrl 'http://${settings.plexServerIP}:32400/status/sessions?X-Plex-Token=${state.authenticationToken}'>\n")
    }
}

def pwhset() {
    def html = """<html><head><title>Plex2SmartThings Settings</title></head><body><h1>
        ${getFullLocalApiServerUrl()}/pwh?access_token=${state.accessToken}<br />

    </h1></body></html>"""
    render contentType: "text/html", data: html, status: 200
}

def p2stset() {
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



/***********************************************************
** Plex Authentication
************************************************************/

def authPage() {
    return dynamicPage(name: "authPage", nextPage: authPage2, install: false) {
        def hub = location.hubs[0]
        section("Plex Login Details") {
        	input "plexUserName", "text", "title": "Plex Username", multiple: false, required: true
    		input "plexPassword", "password", "title": "Plex Password", multiple: false, required: true
            input "plexServerIP", "text", "title": "Server IP", multiple: false, required: true
		}
    }
}
def authPage2() {
    getAuthenticationToken()
    clientPage()
}

def getAuthenticationToken() {
    log.debug "Getting authentication token for Plex Server " + settings.plexServerIP      
    def paramsp = [
    	uri: "https://plex.tv/users/sign_in.json?user%5Blogin%5D=" + settings.plexUserName + "&user%5Bpassword%5D=" + URLEncoder.encode(settings.plexPassword),
        requestContentType: "application/json",
        headers: [
            'X-Plex-Client-Identifier': 'PlexCommunicator',
			'X-Plex-Product': 'Plex Communicator',
			'X-Plex-Version': '1.0'
        ]
   	]    
	try {    
		httpPost(paramsp) { resp ->          
        	state.authenticationToken = resp.data.user.authentication_token;
        	log.debug "Congratulations Token recieved: " + state.authenticationToken + " & your Plex Pass status is " + resp.data.user.subscription.status }
	}
	catch (Exception e) { log.warn "Hit Exception $e on $paramsp" }
}

/***********************************************************
** CLIENTS
************************************************************/

def clientPage() {
    getClients()
    def devs = getClientList()
	return dynamicPage(name: "clientPage", title: "NOTE:", nextPage: mainPage, uninstall: false, install: true) {
        section("If your device does not appear in the list"){}
        section("Devices currently in use by plex will have a [►] icon next to them, this can be helpful when multiple devices share the same name, if a device is playing but not shown then press Save above and come back to this screen"){
        	input "devices", "enum", title: "Select Your Devices", options: devs, multiple: true, required: false, submitOnChange: true
  		}
        if(!devices){
            section("*CAUTION*"){
            	href(name: "clearClients", title:"RESET Devices List", description: "", page: "clearClients", required: false)
            }
        }else{
        section("To Reset Devices List - Untick all devices in the list above, and the option to reset will appear"){}
        }
    }
}


def clearClients() {
	state.plexClients = [:]
    mainPage()
}

def getClientList() {
    def devList = [:]
    state.plexClients.each { id, details -> devList << [ "$id": "${details.name}" ] }
    state.playingClients.each { id, details -> devList << [ "$id": "${details.name} [►]" ] }
    return devList.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() }
}

def getClients(){
    // set lists
	def isMap = state.plexClients instanceof Map
    if(!isMap){state.plexClients = [:]}
    def isMap2 = state.playingClients instanceof Map
    if(!isMap2){state.playingClients = [:]}
    // Get devices.xml clients
    getClientsXML()
    // Request server:32400/status/sessions clients - chrome cast for example is not in devices.
	executeRequest("/status/sessions", "GET")
}

def executeRequest(Path, method) {    
	def headers = [:] 
	headers.put("HOST", "$settings.plexServerIP:32400")
    headers.put("X-Plex-Token", state.authenticationToken)	
	try {    
		def actualAction = new hubitat.device.HubAction(
		    method: method,
		    path: Path,
		    headers: headers)
		sendHubCommand(actualAction)   
	}
	catch (Exception e) {log.debug "Hit Exception $e on $hubAction"}
}

def response(evt) {	 
	// Reponse to hub from now playing request    
    def msg = parseLanMessage(evt.description);
    def stillPlaying = []
    if(msg && msg.body && msg.body.startsWith("<?xml")){
    	log.debug "Parsing status/sessions"
    	def whatToCallMe = ""
    	def playingDevices = [:]
    	def mediaContainer = new XmlSlurper().parseText(msg.body)
		mediaContainer.Video.each { thing ->
            if(thing.Player.@title.text() != "") 		{whatToCallMe = "${thing.Player.@title.text()}-${thing.Player.@product.text()}"}
        	else if(thing.Player.@device.text()!="")	{whatToCallMe = "${thing.Player.@device.text()}-${thing.Player.@product.text()}"}
            playingDevices << [ (thing.Player.@machineIdentifier.text()): [name: "${whatToCallMe}", id: "${thing.Player.@machineIdentifier.text()}"]]
            
            if(settings?.stPoller){
    			def plexEvent = [:] << [ id: "${thing.Player.@machineIdentifier.text()}", type: "${thing.@type.text()}", status: "${thing.Player.@state.text()}", user: "${thing.User.@title.text()}" ]
                stillPlaying << "${thing.Player.@machineIdentifier.text()}"
        		eventHandler(plexEvent)
            }
        }
        if(settings?.stPoller){
        	//stop anything that's no long visible in the playing list but was playing before
        	state.playingClients.each { id, data ->
            	if(!stillPlaying.contains("$id")){
                	def plexEvent2 = [:] << [ id: "${id}", type: "--", status: "stopped", user: "--" ]
                    eventHandler(plexEvent2)
                }
            }
        }
        state.plexClients << playingDevices
        state.playingClients = playingDevices
    }
}


def getClientsXML() {
    //getAuthenticationToken()
    log.warn state.authenticationToken
    def xmlDevices = [:]
    // Get from Devices List
    def paramsg = [
		uri: "https://plex.tv/devices.xml",
		contentType: 'application/xml',
		headers: [ 'X-Plex-Token': state.authenticationToken ]
	]
	httpGet(paramsg) { resp ->
        log.debug "Parsing plex.tv/devices.xml"
        def devices = resp.data.Device
        devices.each { thing ->        
        	// If not these things
        	if(thing.@name.text()!="Plex Communicator" && !thing.@provides.text().contains("server")){      	
            	//Define name based on name unless blank then use device name
                def whatToCallMe = "Unknown"
                if(thing.@name.text() != "") 		{whatToCallMe = "${thing.@name.text()}-${thing.@product.text()}"}
                else if(thing.@device.text()!="")	{whatToCallMe = "${thing.@device.text()}-${thing.@product.text()}"}  
                xmlDevices << [ (thing.@clientIdentifier.text()): [name: "${whatToCallMe}", id: "${thing.@clientIdentifier.text()}"]]
        	}
    	}   
    }
    //Get from status
    state.plexClients << xmlDevices
}

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
		uri: "http://192.168.1.43:8096/emby/Items?Fields=Tags&Ids=$movieId&api_key=edd2dd1347c04728bd584347f9a57f1c",
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
		uri: "http://192.168.1.43:8096/emby/Sessions?DeviceId=$clientUuid&api_key=edd2dd1347c04728bd584347f9a57f1c",
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
	log.debug "Launching Vudu app on roku using http://$clientIp:8060/launch/13842?contentId=$contentId"
    def paramsp = [
		uri: "http://$clientIp:8060/launch/13842?contentId=$contentId",
		contentType: 'application/json'
	]
	httpPost(paramsp) { resp ->
		log.debug resp.data }
		}