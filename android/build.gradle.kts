plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// ---------------------------------------------------------------------------
// Play-policy guard (B-091): the reader is read-only FOREVER.
//
// The single bright line separating a permitted decision-support overlay from an auto-banned
// "acts on another app's UI" trojan signature is autonomous action. This task makes that a build
// invariant instead of a convention: it fails the build if any accessibility-automation API enters
// production source, or if the accessibility-service declaration loses its read-only posture.
// Wired into every module's `check` and run explicitly in CI. Do NOT weaken or bypass this task —
// auto-accept/auto-decline is a product non-goal (docs/play-compliance.md).
//
// Scope: it's a tripwire for the DIRECT, ordinary way automation would creep in (a literal
// performAction/dispatchGesture/performGlobalAction call). It does not defeat deliberate obfuscation
// (reflection, a name split across lines) — that's on code review — but it makes the read-only
// invariant explicit and regression-proof against the accidental addition, which is the real risk.
// ---------------------------------------------------------------------------

val verifyReadOnlyCapture = tasks.register("verifyReadOnlyCapture") {
    group = "verification"
    description =
        "Fails if accessibility-automation APIs enter production code or the a11y service " +
            "config loses its read-only declaration (Play policy, B-091)."

    val rootPath = rootDir
    inputs.files(
        fileTree(rootDir) {
            include("*/src/**/*.kt", "*/src/**/*.java", "*/src/**/res/xml/*.xml")
            exclude("**/build/**", "**/src/test/**", "**/src/androidTest/**")
        },
    )
    outputs.upToDateWhen { false }

    doLast {
        val bannedAutomationTokens = listOf(
            // Acting on another app's nodes/UI.
            "performAction(",
            "dispatchGesture(",
            "performGlobalAction(",
            "GLOBAL_ACTION_",
        )
        // The accessibility-service config(s) must keep these invariants.
        val requiredConfigTokens = listOf("android:isAccessibilityTool=\"false\"")
        val bannedConfigTokens = listOf(
            "canPerformGestures=\"true\"",
            "flagRequestFilterKeyEvents",
            "flagRequestTouchExploration",
        )

        val violations = mutableListOf<String>()
        val serviceConfigs = mutableListOf<java.io.File>()

        // Scan every PRODUCTION source set (src/main, src/release, src/debug, flavors…) — automation
        // must not enter any of them. Only test/androidTest are exempt (a test may name a banned API).
        rootPath.listFiles().orEmpty().filter { it.isDirectory }.forEach { module ->
            val srcRoot = java.io.File(module, "src")
            if (!srcRoot.isDirectory) return@forEach
            srcRoot.listFiles().orEmpty()
                .filter { it.isDirectory && it.name != "test" && it.name != "androidTest" }
                .forEach { sourceSet ->
                    sourceSet.walkTopDown().filter { it.isFile }.forEach { file ->
                        when {
                            file.extension == "kt" || file.extension == "java" -> {
                                val text = file.readText()
                                bannedAutomationTokens.forEach { token ->
                                    if (text.contains(token)) {
                                        violations +=
                                            "${file.relativeTo(rootPath)}: uses banned automation API `$token`"
                                    }
                                }
                            }
                            file.extension == "xml" && file.parentFile.name == "xml" -> {
                                if (file.readText().contains("<accessibility-service")) {
                                    serviceConfigs += file
                                }
                            }
                        }
                    }
                }
        }

        if (serviceConfigs.isEmpty()) {
            violations += "no accessibility-service config found under */src/*/res/xml — " +
                "the read-only declaration (isAccessibilityTool=\"false\") must exist"
        }
        serviceConfigs.forEach { file ->
            // Strip XML comments: the config documents WHY the banned capabilities are absent.
            val text = file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            requiredConfigTokens.forEach { token ->
                if (!text.contains(token)) {
                    violations += "${file.relativeTo(rootPath)}: missing required declaration `$token`"
                }
            }
            bannedConfigTokens.forEach { token ->
                if (text.contains(token)) {
                    violations += "${file.relativeTo(rootPath)}: declares banned capability `$token`"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "READ-ONLY POLICY VIOLATION (Play compliance, B-091).\n" +
                    "The reader informs, it never acts — no auto-accept, no auto-decline, no " +
                    "tapping on the driver's behalf. See docs/play-compliance.md.\n\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyReadOnlyCapture) }
}
