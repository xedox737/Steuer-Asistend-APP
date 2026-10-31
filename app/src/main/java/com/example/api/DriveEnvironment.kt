package com.example.api

import com.example.BuildConfig
import com.example.data.DriveAppConfig
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One namespace policy for both builds; identity is never a user setting. */
object DriveEnvironment {
    private val policy = DriveNamespacePolicy(BuildConfig.APPLICATION_ID)
    private val readProof = ThreadLocal<DriveReadProof?>()

    internal suspend fun <T> withReadProof(token: String, send: (Request) -> Response, block: suspend () -> T): T {
        val scope = DriveReadProof("Bearer $token")
        return withContext(readProof.asContextElement(scope)) {
            try {
                val result = block()
                currentCoroutineContext().ensureActive()
                policy.verifyReadProof(scope, send)
                result
            } finally { scope.clear() }
        }
    }

    internal fun invalidateReadProof() { readProof.get()?.clear() }
    val namespaceGuardEnabled: Boolean get() = true
    const val PRODUCTION_ROOT = "Steuerassistent Belege"
    const val PRODUCTION_APP_IDENTIFIER = "ImmobilienBelegApp"
    val rootFolderName: String get() = policy.rootFolderName
    val appIdentifier: String get() = policy.appIdentifier
    val isIsolatedTest: Boolean get() = policy.isIsolatedTest

    internal fun execute(request: Request, send: (Request) -> Response): Response =
        policy.execute(request, send, readProof.get())

    internal fun validateConfig(config: DriveAppConfig, token: String, rootId: String?, systemId: String?, send: (Request) -> Response) =
        policy.validateConfig(config, token, rootId, systemId, send, readProof.get())

    internal fun validateJsonReferences(json: String, token: String, send: (Request) -> Response) =
        policy.validateJsonReferences(json, token, send)
    internal fun validateRestoreJsonReferences(json: String, token: String, send: (Request) -> Response): Set<String> =
        policy.validateRestoreJsonReferences(json, token, send, readProof.get())

}
