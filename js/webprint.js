/**
 * This file is part of WebPrint
 *
 * @author Michael Wallace
 *
 * Copyright (C) 2015 Michael Wallace, WallaceIT
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
var WebPrint = function (init, opt) {
    var options = {
        relayHost: "127.0.0.1",
        relayPort: "8080",
        listPrinterCallback: null,
        listPortsCallback: null,
        readyCallback: null
    };

    $.extend(options, opt);

    this.printRaw = function (data, printer, encoding) {
        var request;
        if (encoding) {
            request = {a: "printraw", printer: printer, data: BASE64.encode(data), encoding: encoding};
        }
        else {
            request = {a: "printraw", printer: printer, data: btoa(data)};
        }
        sendAppletRequest(request);
    };

    this.printSerial = function (data, port, encoding) {
        if (isAndroid){
            alert("Serial port printing is not available in Android.");
            return;
        }
        var request;
        if (encoding) {
            request = {a: "printraw", port: port, data: BASE64.encode(data), encoding: encoding};
        }
        else {
            request = {a: "printraw", port: port, data: btoa(data)};
        }
        sendAppletRequest(request);
    };

    this.printTcp = function (data, socket, encoding) {
        var request;
        if (encoding) {
            request = {a: "printraw", socket: socket, data: BASE64.encode(data), encoding: encoding};
        }
        else {
            request = {a: "printraw", socket: socket, data: btoa(data)};
        }
        sendAppletRequest(request);
    };

    this.printHtml = function (data, printer, pageSetting, encoding) {
        if (isAndroid){
            alert("HTML printing is not available in Android.");
            return;
        }
        var request = {a: "printhtml", printer: printer, data: data, pageSetting: pageSetting, encoding: encoding};
        sendAppletRequest(request);
    };
    /*
     * Opens a port using the specified settings
     * @param port String eg. COM1 / TTY0
     * @param settings Object eg. {baud:9600, databits:8, stopbits:1, parity:even, flow:"none"}
     */
    this.openPort = function (port, settings) {
        var request = {a: "openport", port: port, settings: settings};
        sendAppletRequest(request);
    };

    this.requestPrinters = function () {
        sendAppletRequest({a: "listprinters"});
    };

    this.requestPorts = function () {
        if (!isAndroid)
            sendAppletRequest({a: "listports"});
    };

    function sendAppletRequest(data) {
        data.cookie = cookie;
        if (!wpwindow || wpwindow.closed || !wpready) {
            if (wpready){
                openPrintWindow();
            } else {
                retry = true;
                checkRelay();
                console.log("Print applet connection not established...trying to reconnect");
            }
            setTimeout(function () {
                wpwindow.postMessage(JSON.stringify(data), "*");
            }, 220);
        }
        wpwindow.postMessage(JSON.stringify(data), "*");
    }

    var wpwindow;
    var wpready = false;
    function openPrintWindow() {
        wpready = false;
        if($("#ifmWebPrint").length > 0){
            var ifm = $("#ifmWebPrint").attr("src", "http://"+options.relayHost+":"+options.relayPort+"/printwindow");

            ifm.load(function(){
                console.log("reload print...");
                wpwindow =  document.getElementById( 'ifmWebPrint' ).contentWindow;
            });
        }else{
            var ifm = $("<iframe>").attr({
                "id": "ifmWebPrint",
                "src": "http://"+options.relayHost+":"+options.relayPort+"/printwindow",
            }).hide();

            $("body").append(ifm);

            wpwindow =  document.getElementById( 'ifmWebPrint' ).contentWindow;

        }

        // wpwindow = window.open("http://"+options.relayHost+":"+options.relayPort+"/printwindow", 'WebPrintService');
        // if (wpwindow)
        //     wpwindow.blur();
        // window.focus();
    }

    this.checkConnection = function () {
        retry = true;
        checkRelay();
    };

    var wptimeOut;
    var retry = true;
    function checkRelay() {
        if (wpwindow && !wpwindow.closed) {
            wpwindow.close();
        }
        window.addEventListener("message", handleWebPrintMessage, false);
        openPrintWindow();
        wptimeOut = setTimeout(dispatchWebPrint, 2000);
        retry = false; // prevent reconnection after one attempt, until user initiates it
    }

    function handleWebPrintMessage(event) {
        if (event.origin != "http://"+options.relayHost+":"+options.relayPort)
            return;
        switch (event.data.a) {
            case "init":
                clearTimeout(wptimeOut);
                wpready = true;
                sendAppletRequest({a:"init"});
                break;
            case "response":
                var response = JSON.parse(event.data.json);
                if (response.hasOwnProperty('ports')) {
                    if (options.listPortsCallback instanceof Function)
                        options.listPortsCallback(response.ports);
                } else if (response.hasOwnProperty('printers')) {
                    if (options.listPrinterCallback instanceof Function)
                        options.listPrinterCallback(response.printers);
                } else if (response.hasOwnProperty('error')) {
                    alert(response.error);
                }
                if (response.hasOwnProperty("cookie")){
                    cookie = response.cookie;
                    localStorage.setItem("webprint_auth", response.cookie);
                }
                if (response.hasOwnProperty("ready")){
                    if (options.readyCallback instanceof Function) options.readyCallback();
                }
                break;
            case "error": // cannot contact print applet from relay window
                if (retry)
                    checkRelay();
        }
        //alert("The Web Printing service has been loaded in a new tab, keep it open for faster printing.");
    }

    function dispatchWebPrint() {
        var answer = confirm("Cannot communicate with the printing app.\nWould you like to open/install the printing app?");
        if (answer) {
            if (isAndroid){
                deployAndroid();
                return;
            }
//                var installFile="WebPrint.jar";
            if (navigator.appVersion.indexOf("Win")!=-1) {
                var installFile="WebPrint_windows_1_1_3.exe";
                window.open("https://my.letuseat.net/webprint/installer/"+installFile, '_blank');
            }else{
                // not support
            }
//            if (navigator.appVersion.indexOf("Mac")!=-1) installFile="WebPrint_macos_1_1_1.dmg";
//            if (navigator.appVersion.indexOf("X11")!=-1) installFile="WebPrint_unix_1_1_1.sh";
//            if (navigator.appVersion.indexOf("Linux")!=-1) installFile="WebPrint_unix_1_1_1.sh";
        }
    }

    function deployAndroid(){
        if (isAndroidIntentSupported()){
            deployAndroidChrome();
        } else {
            deployAndroidFirefox();
        }
        //document.location.href = "intent://#Intent;scheme=webprint;package=app.com.trizesolutions.webprint;S.browser_fallback_url=play.google.com;end";
    }

    function isAndroidIntentSupported() {
        var isChrome = navigator.userAgent.toLowerCase().indexOf('chrome') > -1;
        if (isChrome) {
            var version = parseInt(window.navigator.appVersion.match(/Chrome\/(\d+)\./)[1], 10);
            return version >= 25;
        } else {
            return false;
        }
    }

    function deployAndroidChrome(){
        // this link needs to be clicked by the user
        var html = '<div id="intent_link" style="position: fixed; top:40%; width: 120px; background-color: white; left:50%; margin-left: -60px; border: solid 2px rgb(75, 75, 75); font-family: Helvetica SansSerif sans-serif; text-align: center; padding: 5px;">' +
            '<a onclick="window.location=\'intent://trize#Intent;scheme=webprint;category=android.intent.category.BROWSABLE;package=com.trizesolutions.webprint;S.browser_fallback_url=https://play.google.com/store/apps/details?id=app.com.trizesolutions.webprint;end\'; document.getElementById(\'intent_link\').remove();">Click To Open WebPrint</a></div>';
        document.body.innerHTML += html;
    }

    function deployAndroidFirefox() {
        var timeout = setTimeout(function() {
           window.location = "https://play.google.com/store/apps/details?id=app.com.trizesolutions.webprint";
        }, 1000);
        window.addEventListener("pagehide", function(evt) {
            clearTimeout(timeout);
        });

        window.location = "webprint://open";
    }
    
    var cookie = localStorage.getItem("webprint_auth");
    if (cookie==null){
        cookie = "";
    }

    var isAndroid = navigator.appVersion.indexOf("Android")!=-1;

    if (init) checkRelay();
    
    return this;
};