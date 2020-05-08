package com.sudoplatform.sudoconfigmanager

import android.content.Context
import com.sudoplatform.sudologging.Logger
import org.json.JSONObject

/**
 * Interface that encapsulates the APIs common to all configuration manager implementations.
 * A configuration manager is responsible for locating the platform configuration file (sudoplatformconfig.json)
 * in the app bundle, parsing it and returning the configuration set specific to a given namespace.
 */
interface SudoConfigManager {

    /**
     * Returns the configuration set under the specified namespace.
     * @param namespace Configuration namespace.
     * @return org.json.JSONObject of configuration parameters or null if the namespace does not exist
     */
    fun getConfigSet(namespace: String): JSONObject?
}

/**
 * Default `SudoConfigManager` implementation
 */
class DefaultSudoConfigManager(private val context: Context, private val logger: Logger? = null): SudoConfigManager {
    private val config: JSONObject?
        get() {
            return try {
                val rawJson =
                    context.assets.open("sudoplatformconfig.json").bufferedReader().use {
                        it.readText()
                    }
                val strippedJson = rawJson.replace("\n", "")
                val jsonObject = JSONObject(strippedJson)
                logger?.info("Loaded the config: $rawJson")
                jsonObject
            } catch (e: Exception) {
                val message = "Failed to load config: $e"
                logger?.error(message)
                logger?.outputError(Error(e))
                null
            }
        }

    override fun getConfigSet(namespace: String): JSONObject? {
        try {
            val configSet = config?.get(namespace) as JSONObject?
            return configSet
        } catch (e: Exception) {
            logger?.info("Namespace: '$namespace' does not exist")
            return null
        }
    }
}