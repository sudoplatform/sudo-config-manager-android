/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoconfigmanager

import android.content.Context
import com.sudoplatform.sudologging.Logger
import org.json.JSONObject
import java.io.Serializable
import java.util.Date

/**
 * Service compatibility information.
 *
 * @param name name of the service associated with the compatibility info. This matches one of the
 *  service name present in sudoplatformconfig.json.
 * @param configVersion version of the service config present in sudoplatformconfig.json. It defaults
 *  to 1 if not present.
 * @param minSupportedVersion minimum supported service config version currently supported by the backend.
 * @param deprecatedVersion any service config version less than or equal to this version is considered
 *  deprecated and the backend may remove the support for those versions after a grace period.
 * @param deprecationGrace after this time any deprecated service config versions will no longer be
 *  compatible with the backend. It is recommended to warn the user prior to the deprecation grace.
 */
data class ServiceCompatibilityInfo(
    val name: String,
    val configVersion: Int,
    val minSupportedVersion: Int?,
    val deprecatedVersion: Int?,
    val deprecationGrace: Date?
) : Serializable

/**
 * Result returned by `validateConfig` API if an incompatible client config is found when compared
 * to the deployed backend services.
 *
 * @param incompatible list of incompatible services. The client must be upgraded to the latest
 *  version in order to use these services.
 * @param deprecated list of services that will be made incompatible with the current version of the
 *  client. The users should be warned that after the specified grace period these services will be
 *  made incompatible.
 */
data class ValidationResult(
    val incompatible: List<ServiceCompatibilityInfo>,
    val deprecated: List<ServiceCompatibilityInfo>
)

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

    /**
     * Validates the client configuration (sudoplatformconfig.json) against the currently deployed set of
     * backend services. If the client configuration is valid, i.e. the client is compatible will all deployed
     * backend services, then the call will complete with `success` result. If any part of the client
     * configuration is incompatible then a detailed information on the incompatible service will be
     * returned in `failure` result. See `SudoConfigManagerError.compatibilityIssueFound` for more details.
     *
     * @return validation result with the details of incompatible or deprecated service configurations.
     */
    suspend fun validateConfig(): ValidationResult
}

/**
 * Default `SudoConfigManager` implementation
 */
class DefaultSudoConfigManager(context: Context, private val logger: Logger? = null, s3Client: S3Client? = null): SudoConfigManager {

    companion object {
        private const val CONFIG_NAMESPACE_IDENTITY_SERVICE = "identityService"

        private const val CONFIG_REGION = "region"
        private const val CONFIG_SERVICE_INFO_BUCKET = "serviceInfoBucket"
    }

    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when publishing to maven central.
     * In order to retry a failed publish without needing to change any functionality, we need a way to generate a different checksum
     * for the source code.  We can change the value of this property which will generate a different checksum for publishing
     * and allow us to retry.  The value of `version` doesn't need to be kept up-to-date with the version of the code.
     */
    val version: String = "3.1.0"

    private val config: JSONObject
    private val s3Client: S3Client?

    init {
        val rawJson =
            context.assets.open("sudoplatformconfig.json").bufferedReader().use {
                it.readText()
            }
        val strippedJson = rawJson.replace("\n", "")
        this.config = JSONObject(strippedJson)
        logger?.info("Loaded the config: $rawJson")

        val identityServiceConfig = this.config.optJSONObject(CONFIG_NAMESPACE_IDENTITY_SERVICE)
        val region = identityServiceConfig?.opt(CONFIG_REGION) as String?
        val serviceInfoBucket = identityServiceConfig?.opt(CONFIG_SERVICE_INFO_BUCKET) as String?


        if(identityServiceConfig != null && region != null && serviceInfoBucket != null) {
            this.s3Client = s3Client ?: DefaultS3Client(region, serviceInfoBucket, this.logger)
        } else this.s3Client = null
    }

    override fun getConfigSet(namespace: String): JSONObject? {
        return try {
            val configSet = config.get(namespace) as JSONObject?
            configSet
        } catch (e: Exception) {
            logger?.info("Namespace: '$namespace' does not exist")
            null
        }
    }

    override suspend fun validateConfig(): ValidationResult {
        this.logger?.info("Validating client configuration against backend.")

        val incompatible = mutableListOf<ServiceCompatibilityInfo>()
        val deprecated =  mutableListOf<ServiceCompatibilityInfo>()

        if(this.s3Client != null) {
            val keys = this.s3Client.listObjects()

            // Only fetch the service info docs for the services that are present in client config
            // to minimize the network calls.
            val servicesInfoToFetch = keys.filter { it.endsWith(".json") && this.config.has(it.removeSuffix(".json")) }
            servicesInfoToFetch.forEach {
                val data = this.s3Client.getObject(it)
                val jsonObject = JSONObject(String(data))
                val serviceName = jsonObject.keys().next()
                if (serviceName != null) {
                    val serviceInfo = jsonObject.optJSONObject(serviceName)
                    val serviceConfig = this.config.optJSONObject(serviceName)
                    if(serviceInfo != null && serviceConfig != null) {
                        val currentVersion = serviceConfig.opt("version") as Int? ?: 1
                        val deprecationGrace = serviceInfo.optLong("deprecationGrace", -1L)
                        val compatibilityInfo = ServiceCompatibilityInfo(
                            serviceName,
                            currentVersion,
                            serviceInfo.opt("minVersion") as Int?,
                            serviceInfo.opt("deprecated") as Int?,
                            if (deprecationGrace != -1L) Date(deprecationGrace) else null
                        )

                        // If the service config in `sudoplatformconfig.json` is less than the
                        // minimum supported version then the client is incompatible.
                        if(currentVersion < (compatibilityInfo.minSupportedVersion ?: 0)) {
                            incompatible.add(compatibilityInfo)
                        }

                        // If the service config is less than or equal to the deprecated version
                        // then it will be made incompatible after the deprecation grace.
                        if(currentVersion <= (compatibilityInfo.deprecatedVersion ?: 0)) {
                            deprecated.add(compatibilityInfo)
                        }
                    }
                }
            }
        }

        return ValidationResult(incompatible, deprecated)
    }
}
