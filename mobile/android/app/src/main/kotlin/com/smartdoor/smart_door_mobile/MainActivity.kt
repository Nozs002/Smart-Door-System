package com.smartdoor.smart_door_mobile

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent
import android.provider.Settings
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "smartdoor/network")
            .setMethodCallHandler { call, result ->
                if (call.method == "openWifiSettings") {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    result.success(null)
                } else if (call.method == "requestSetupNode") {
                    val path = call.argument<String>("path")
                    val setupCode = call.argument<String>("setupCode") ?: ""
                    val body = call.argument<Map<String, Any?>>("body")
                    if (path !in listOf("/api/info", "/api/networks", "/api/wifi")) {
                        result.error("invalid_path", "Invalid setup endpoint", null)
                        return@setMethodCallHandler
                    }
                    // Use the setup network while Android keeps its usual route to the backend.
                    Thread {
                        var connection: HttpURLConnection? = null
                        try {
                            val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                            val wifi = manager.allNetworks.filter { network ->
                                val isWifi = manager.getNetworkCapabilities(network)
                                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                                isWifi && manager.getLinkProperties(network)?.linkAddresses?.any { link ->
                                    val address = link.address
                                    address is Inet4Address &&
                                        address.hostAddress?.startsWith("192.168.4.") == true
                                } == true
                            }.singleOrNull() ?: throw IllegalStateException("Connect to a single Node Wi-Fi first")
                            connection = wifi.openConnection(URL("http://192.168.4.1$path")) as HttpURLConnection
                            connection.useCaches = false
                            connection.connectTimeout = 5000
                            connection.readTimeout = 10000
                            connection.instanceFollowRedirects = false
                            connection.setRequestProperty("X-Setup-Code", setupCode)
                            if (body != null) {
                                connection.requestMethod = "POST"
                                connection.doOutput = true
                                connection.setRequestProperty("Content-Type", "application/json")
                                connection.outputStream.use { stream ->
                                    stream.write(JSONObject(body).toString().toByteArray(Charsets.UTF_8))
                                }
                            }
                            val status = connection.responseCode
                            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                            val bytes = java.io.ByteArrayOutputStream()
                            stream?.use { input ->
                                val buffer = ByteArray(1024)
                                var count = input.read(buffer)
                                while (count != -1) {
                                    if (bytes.size() + count > 8192) throw IllegalStateException("Setup response too large")
                                    bytes.write(buffer, 0, count)
                                    count = input.read(buffer)
                                }
                            }
                            val response = mapOf("status" to status, "body" to bytes.toString("UTF-8"))
                            runOnUiThread { result.success(response) }
                        } catch (error: Exception) {
                            runOnUiThread { result.error("setup_connection", "Cannot reach Node over Wi-Fi", null) }
                        } finally {
                            connection?.disconnect()
                        }
                    }.start()
                } else {
                    result.notImplemented()
                }
            }
    }
}
