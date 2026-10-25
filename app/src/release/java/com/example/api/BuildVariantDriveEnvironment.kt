package com.example.api

import com.example.data.DriveAppConfig
import okhttp3.Request
import okhttp3.Response

internal object BuildVariantDriveEnvironment {
    val rootFolderName: String get() = DriveEnvironment.PRODUCTION_ROOT
    val appIdentifier: String get() = DriveEnvironment.PRODUCTION_APP_IDENTIFIER
    const val isIsolatedTest = false
    fun execute(request: Request, send: (Request) -> Response): Response = send(request)
    @Suppress("UNUSED_PARAMETER")
    fun validateConfig(config: DriveAppConfig, token: String, rootId: String?, systemId: String?, send: (Request) -> Response) = Unit
    @Suppress("UNUSED_PARAMETER")
    fun validateJsonReferences(json: String, token: String, send: (Request) -> Response) = Unit
}
