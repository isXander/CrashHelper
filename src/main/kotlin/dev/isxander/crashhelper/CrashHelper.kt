package dev.isxander.crashhelper

import com.google.common.io.Resources
import com.google.gson.JsonArray
import dev.isxander.crashhelper.utils.JsonObjectExt
import gg.essential.universal.UDesktop
import net.minecraft.client.Minecraft
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection
import javax.swing.JOptionPane

object CrashHelper {

    // for some reason it triggers twice idk
    private var scannedReport: LinkedHashMap<String, ArrayList<String>>? = null

    private val RAM_REGEX = Pattern.compile("-Xmx(?<ram>\\d+)(?<type>[GMK])", Pattern.CASE_INSENSITIVE)

    @JvmStatic
    fun scanReport(report: String, description: String): String {
        try {
            val responses = scannedReport ?: getResponses(report)

            if (responses.isEmpty()) return "$description (unknown crash)"

            val message = ArrayList(responses.values)[0][0]

            if (scannedReport == null) {
                if (UDesktop.isLinux) try { Minecraft.getMinecraft().mouseHelper.ungrabMouseCursor() } catch (e: Throwable) { e.printStackTrace() }
                val options = arrayOf("Open Crashlog", "Exit to launcher")
                val input = JOptionPane.showOptionDialog(null, message, "Crash Helper", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0])
                if (input == 0) UDesktop.browse(URI.create(uploadToHastebin(convertResponsesToString(responses, report))))
            }
            scannedReport = responses

            return message
        } catch (e: Throwable) {
            e.printStackTrace()
            return description
        }
    }

    private fun getResponses(report: String): LinkedHashMap<String, ArrayList<String>> {
        val issues = JsonObjectExt(Resources.toString(URL("https://raw.githubusercontent.com/isXander/MinecraftIssues/main/issues.json"), StandardCharsets.UTF_8))
        val responses = linkedMapOf<String, ArrayList<String>>()

        for (category in issues.keys) {
            for (categoryElement in issues[category, JsonArray()]!!) {
                val issue = JsonObjectExt(categoryElement.asJsonObject)
                var info = issue["info", ""]!!
                if (info.isEmpty() && !issue.has("hardcode")) continue

                var andCheck = true
                var orCheck = true

                if (issue.has("hardcode")) {
                    when (issue["hardcode", "unknown"]!!.lowercase()) {
                        "ram" -> {
                            val matcher = RAM_REGEX.matcher(report)
                            if (matcher.find()) {
                                var ram = Integer.parseInt(matcher.group("ram"))
                                val type = matcher.group("type")
                                if (type.equals("G", true)) ram *= 1024
                                if (type.equals("K", true)) ram /= 1000
                                if (ram > 4096) info = "You are using more than 4GB of ram. This can cause issues and is generally un-needed - even on high-end PCs."

                            }
                        }
                    }
                } else {
                    for (checkElement in issue["and", JsonArray()]!!) {
                        val check = JsonObjectExt(checkElement.asJsonObject)

                        var outcome = true
                        when (check["method", "contains"]!!.lowercase()) {
                            "contains" -> outcome = report.contains(check["value", "ouughaughaygajhgajhkgahjk"]!!)
                            "regex" -> outcome = Pattern.compile(check["value", "ouughaughaygajhgajhkgahjk"]!!, Pattern.CASE_INSENSITIVE).matcher(report).find()
                        }
                        if (check["not", false]) outcome = !outcome

                        if (!outcome) {
                            andCheck = false
                            break
                        }
                    }

                    if (issue.has("or")) {
                        orCheck = false
                        for (checkElement in issue["or", JsonArray()]!!) {
                            val check = JsonObjectExt(checkElement.asJsonObject)

                            var outcome = true
                            when (check["method", "contains"]!!.lowercase()) {
                                "contains" -> outcome = report.contains(check["value", "ouughaughaygajhgajhkgahjk"]!!)
                                "regex" -> outcome = Pattern.compile(check["value", "ouughaughaygajhgajhkgahjk"]!!, Pattern.CASE_INSENSITIVE).matcher(report).find()
                            }
                            if (check["not", false]) outcome = !outcome

                            if (outcome) {
                                orCheck = true
                                break
                            }
                        }
                    }
                }

                if (andCheck && orCheck) {
                    responses.putIfAbsent(category, arrayListOf())
                    responses[category]!!.add(info)
                }
            }
        }

        return responses
    }

    private fun convertResponsesToString(responses: Map<String, ArrayList<String>>, report: String): String {
        val sb = StringBuilder()
        for ((category, infoList) in responses) {
            sb.append("------- $category -------\n")
            for (info in infoList) {
                sb.append("$info\n")
            }
            sb.append("\n")
        }

        sb.append("\n\n\n--------------------- \nReport:\n\n")
        sb.append(report)
        sb.append("\n---------------------")

        return sb.toString()
    }

    private fun uploadToHastebin(text: String): String {
        val postData: ByteArray = text.toByteArray(StandardCharsets.UTF_8)
        val postDataLength = postData.size

        val requestURL = "https://hst.sh/documents"
        val url = URL(requestURL)
        val conn: HttpsURLConnection = url.openConnection() as HttpsURLConnection
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.requestMethod = "POST"
        conn.setRequestProperty("User-Agent", "CrashHelper/${CrashHelperInfo.VERSION_FULL}")
        conn.setRequestProperty("Content-Length", postDataLength.toString())
        conn.useCaches = false

        var response: String
        DataOutputStream(conn.outputStream).use { dos ->
            dos.write(postData)
            BufferedReader(InputStreamReader(conn.inputStream)).use { br ->
                response = br.readLine()
            }
        }

        if (response.contains("\"key\"")) {
            response = response.substring(response.indexOf(":") + 2, response.length - 2)
            response = "https://hst.sh/$response"
        }

        return response
    }

}
