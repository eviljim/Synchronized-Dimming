/**
 *  Dims a collection of dimmers together. Changing any one will send events to all of the others.
 *  This is unlike the "Dim With Me" app, which is one-to-many. This is many-to-many.
 *
 *  Copyright 2015 Michael Barnathan (michael@barnathan.name)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

definition(
        name: "Synchronized Dimming",
        namespace: "quantiletree",
        author: "Michael Barnathan",
        description: "Dims a collection of dimmers together. Changing any one will dim all of the others.",
        category: "Convenience",
        iconUrl: "http://cdn.device-icons.smartthings.com/Weather/weather13-icn@2x.png",
        iconX2Url: "http://cdn.device-icons.smartthings.com/Weather/weather13-icn@2x.png"
)

preferences {
    section("Select devices to dim together:") {
        input "dimmers", "capability.switchLevel", required:true, title:"Dimmers", multiple:true
    }
    section("Select switches to turn on/off together:") {
    	input "switches", "capability.switch", required:false, title:"Switches", multiple:true
    }
}

def installed() {
    log.info "Dimmers tied! Settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Dimmer ties updated: ${settings}"
    initialize()
}

def initialize() {
    unsubscribe()
    subscribeLevelTo(dimmers ?: [])
    subscribeSwitchesTo(switches ?: [])
    atomicState.lockTime = 0
    atomicState.lockDevice = ""
}

private subscribeSwitchesTo(devices) {
    subscribe(devices, "switch.on", tieOn)
    subscribe(devices, "switch.off", tieOff)
}

private subscribeLevelTo(devices) {
    subscribe(devices, "level", tieLevel)
    subscribe(devices, "switch.setLevel", tieLevel)
}

def tieLevel(event) {
    def level = event.value as int
    if (level == 0 && event.value != "0") {
        log.warn "Non-numeric dimmer level coming from ${event.dump()}: ${event.value}"
        return
    }

	if (!checkLock(event)) {
	    return
    }
    sendNotificationEvent("${event.displayName} setLevel to ${level}")
    
    def remainingDimmers = (dimmers ?: []).findAll { it != event.device && it.currentValue("level") as int != level }
    if (remainingDimmers) {
        log.trace "${remainingDimmers.size()} dimmers remain to be updated to level ${level}, current atomicState: " + atomicState
        remainingDimmers*.setLevel(level)
    } else {
        log.trace "No remaining dimmers. atomicState: " + atomicState
    }
    
    if (level > 0) {
        log.trace "${remainingDimmers.size()} dimmers remain to be turned on"
    	def remainingSwitches = (switches ?: []).findAll { it != event.device && it.currentValue("switch") != "on" }
        remainingSwitches*.on()
    } else {
        log.trace "${remainingDimmers.size()} dimmers remain to be turned off"
    	def remainingSwitches = (switches ?: []).findAll { it != event.device && it.currentValue("switch") != "off" }
        remainingSwitches*.off()
    }
}

def checkLock(event) {
	log.trace "Current atomicState: " + atomicState
    log.trace "Event's device id: " + event.deviceId + "; display name: " + event.displayName
    if (now() < atomicState.lockTime + 60000 && atomicState.lockDevice != event.deviceId) {
    	log.trace "Ignoring event due to existing lock."
        return false
    }
    atomicState.lockTime = now()
    atomicState.lockDevice = event.deviceId
    log.trace "Setting new atomicState: " + atomicState
    return true
}

def tieOn(event) {
	if (!checkLock(event)) {
	    return
    }
    sendNotificationEvent("${event.displayName} on, turning on ${dimmers.size()} dimmers and ${switches.size()} switches")
    dimmers?.on()
    switches?.on()
}

def tieOff(event) {
	if (!checkLock(event)) {
	    return
    }
    sendNotificationEvent("${event.displayName} off, turning off ${dimmers.size()} dimmers and ${switches.size()} switches")
    dimmers?.off()
    switches?.off()
}
