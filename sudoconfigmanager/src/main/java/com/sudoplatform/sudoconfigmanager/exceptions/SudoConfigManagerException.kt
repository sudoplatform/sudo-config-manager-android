/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoconfigmanager.exceptions

open class SudoConfigManagerException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
    /**
     * Exception for wrapping unexpected exceptions.
     */
    class FailedException(message: String? = null, cause: Throwable? = null) :
        SudoConfigManagerException(message = message, cause = cause)

    /**
     * Exception for indicating the configuration of the client was invalid.
     */
    class InvalidConfigException(message: String? = null, cause: Throwable? = null) :
        SudoConfigManagerException(message = message, cause = cause)

}
