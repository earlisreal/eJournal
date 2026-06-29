package io.earlisreal.ejournal.jcef

import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings

/**
 * JCEF runtime backed by JBR's BUNDLED `jcef` module (org.cef + com.jetbrains.cef.JCefAppConfig) —
 * the production-aligned alternative to [JcefRuntime] (jcefmaven). Must run with `--add-modules jcef`
 * on a JBR that ships the CEF native payload (a `jbr_jcef` build). `JCefAppConfig` computes the CEF
 * framework/helper paths from the runtime's own location, so there is no manual native-path wiring
 * and no ~300MB download — the runtime we ship already carries it.
 *
 * `com.jetbrains.cef.JCefAppConfig` is JBR-only (absent from every Maven artifact), so it's reached
 * reflectively: this file compiles against jcefmaven's classpath `org.cef`, but at runtime
 * `--add-modules jcef` makes the JBR module's `org.cef` shadow it, and both are the standard JCEF API.
 */
object JbrJcefRuntime {
    private const val TAG = "[jbr-jcef]"

    @Volatile private var app: CefApp? = null
    @Volatile private var cefClient: CefClient? = null

    @Synchronized
    fun warmUp() {
        if (app != null) return
        val t = System.currentTimeMillis()
        val cfgClass = Class.forName("com.jetbrains.cef.JCefAppConfig")
        val cfg = cfgClass.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val baseArgs = cfgClass.getMethod("getAppArgs").invoke(cfg) as Array<String>
        val settings = cfgClass.getMethod("getCefSettings").invoke(cfg) as CefSettings
        // Bare Gradle `java` has no .app main bundle, so CEF can't auto-locate icudtl.dat. Point CEF
        // at the framework's Resources explicitly (the dir that holds icudtl.dat + the .lproj packs).
        val fwPath = baseArgs.firstOrNull { it.startsWith("--framework-dir-path=") }
            ?.substringAfter('=')
        val appArgs = if (fwPath != null) baseArgs + "--resources-dir-path=$fwPath/Resources" else baseArgs
        println("$TAG appArgs=${appArgs.joinToString(" ")}")
        check(CefApp.startup(appArgs)) { "CefApp.startup() returned false" }
        @Suppress("DEPRECATION")
        val a = CefApp.getInstance(settings)
        app = a
        cefClient = a.createClient()
        // NOTE: only the cross-compatible core API (startup/getInstance/createClient) is used here.
        // CefApp.getState()/getVersion() diverge between jcefmaven's jcef-api (compile classpath) and
        // JBR's jcef module (runtime), so they're avoided to prevent a linkage mismatch.
        println("$TAG ready in ${System.currentTimeMillis() - t}ms; CefClient created")
    }

    fun client(): CefClient {
        warmUp()
        return cefClient ?: error("CefClient unavailable after warmUp()")
    }

    fun dispose() {
        runCatching { cefClient?.dispose() }
        runCatching { app?.dispose() }
        cefClient = null
        app = null
    }
}
