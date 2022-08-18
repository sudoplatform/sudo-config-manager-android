/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoconfigmanager

import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ListObjectsV2Result
import com.amazonaws.services.s3.model.S3Object
import com.sudoplatform.sudologging.Logger

/**
 * S3 client wrapper protocol mainly used for providing an abstraction layer on top of
 * AWS S3 SDK.
 */
interface S3Client {

    /**
     * AWS region hosting the S3 bucket.
     */
    val region: String

    /**
     * S3 bucket associated with this client.
     */
    val bucket: String

    /**
     * Retrieves a S3 object.
     *
     * @param key S3 key associated with the object.
     * @return retrieved object as [ByteArray].
     */
    suspend fun getObject(key: String): ByteArray

    /**
     * Lists the content of the S3 bucket associated with this client.
     *
     * @return list of object keys.
     */
    suspend fun listObjects(): List<String>

}

/**
 * Default S3 client implementation.
 *
 * @param region AWS region.
 * @param bucket S3 bucket.
 */
class DefaultS3Client (
    override val region: String,
    override val bucket: String,
    private val logger: Logger?,
): S3Client {

    private val amazonS3Client: AmazonS3Client = AmazonS3Client(AnonymousAWSCredentials(), Region.getRegion(region))

    override suspend fun getObject(key: String): ByteArray {
        this.logger?.info("Retrieving S3 object: bucket=${this.bucket}, key=${key}")
        val obj: S3Object = this.amazonS3Client.getObject(this.bucket, key)
        return obj.objectContent.readBytes()
    }

    override suspend fun listObjects(): List<String> {
        this.logger?.info("Listing S3 objects: bucket=${this.bucket}.")

        val result: ListObjectsV2Result = this.amazonS3Client.listObjectsV2(this.bucket)
        val objects = result.objectSummaries
        return objects.map {
            it.key
        }
    }
}
