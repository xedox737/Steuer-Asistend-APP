package com.example.api

import com.example.data.DriveAppConfig
import okhttp3.Request
import okhttp3.Response

/** Build identity, never a user setting. Release delegates without changing its Drive protocol. */
object DriveEnvironment {
    const val PRODUCTION_ROOT = "Steuerassistent Belege"
    const val PRODUCTION_APP_IDENTIFIER = "ImmobilienBelegApp"
    val rootFolderName: String get() = BuildVariantDriveEnvironment.rootFolderName
    val appIdentifier: String get() = BuildVariantDriveEnvironment.appIdentifier
    val isIsolatedTest: Boolean get() = BuildVariantDriveEnvironment.isIsolatedTest

    internal fun execute(request: Request, send: (Request) -> Response): Response =
        BuildVariantDriveEnvironment.execute(request, send)

    internal fun validateConfig(config: DriveAppConfig, token: String, rootId: String?, systemId: String?, send: (Request) -> Response) =
        BuildVariantDriveEnvironment.validateConfig(config, token, rootId, systemId, send)

    internal fun validateJsonReferences(json: String, token: String, send: (Request) -> Response) =
        BuildVariantDriveEnvironment.validateJsonReferences(json, token, send)
}
