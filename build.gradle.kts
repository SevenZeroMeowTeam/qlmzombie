plugins {
    eclipse
    idea
    `maven-publish`
    id("net.minecraftforge.gradle") version "6.0.+"
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
}

import java.time.Instant
import java.util.zip.ZipFile

// MixinGradle：为 Thirst 模块 mixin 生成 refmap，保证正式（SRG）环境下 mixin 正确映射不闪退。
// 原模组 dev.ghen.thirst 同款方案。
buildscript {
    repositories {
        mavenCentral()
        maven("https://maven.minecraftforge.net")
        maven("https://repo.spongepowered.org/repository/maven-public")
    }
    dependencies {
        classpath("org.spongepowered:mixingradle:0.7-SNAPSHOT")
    }
}

apply(plugin = "org.spongepowered.mixin")

val mod_version: String by project
val mod_group_id: String by project
val mod_id: String by project
val mod_license: String by project
val mod_authors: String by project
val minecraft_version: String by project
val minecraft_version_range: String by project
val forge_version: String by project
val forge_version_range: String by project
val loader_version_range: String by project
val mapping_channel: String by project
val mapping_version: String by project
val kotlin_for_forge_version: String by project
val kubejs_version: String by project
val cloth_config_version: String by project
val puzzleslib_version: String by project

// NOTE: Keep Chinese strings hardcoded here instead of reading from gradle.properties.
// Reason: java.util.Properties spec requires gradle.properties to be read as ISO-8859-1,
// which corrupts Chinese characters. This is the root cause of the mojibake (乱码) in
// mods.toml (e.g. "ä¸é¶åµåµå°¸æ«æ¥çå­mod" instead of "七零喵僵尸末日生存mod").
val mod_name = "七零喵僵尸末日生存mod"
val mod_description = "七零喵僵尸末日生存mod (Kotlin+Java+KubeJS重构版) - 基于开源模组整合的末日生存mod"

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

minecraft {
    mappings(mapOf("channel" to mapping_channel, "version" to mapping_version))
    copyIdeResources = true
    // 访问转换器：Thirst-Mod 口渴系统需要公开 FoodProperties.nutrition 字段（原模组 dev.ghen.thirst 同款）
    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            property("file.encoding", "UTF-8")
            property("sun.stdout.encoding", "UTF-8")
            property("sun.stderr.encoding", "UTF-8")
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir("src/main/java")
            srcDir("src/main/kotlin")
        }
        resources {
            srcDir("src/generated/resources")
            srcDir("src/main/resources")
            srcDir("src/main/kubejs")
        }
    }
}

repositories {
    mavenCentral()
    flatDir {
        dirs("src/main/libs")
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:${minecraft_version}-${forge_version}")

    // All mod JARs from libs directory
    // KotlinForForge and KubeJS are runtime-only (not referenced directly in code)
    implementation(fileTree(mapOf("dir" to "src/main/libs", "include" to listOf("*.jar"))))

    // Mixin 注解处理器：生成 refmap，保证 Thirst 模块 mixin 在正式（SRG）环境下正确映射
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

// ======================================================================
// JarInJar 内嵌 KotlinForForge（修复 "requires kotlinforforge 4.12 or above / not installed"）
// ======================================================================
// 背景：mods.toml 将 kotlinforforge 声明为 mandatory 依赖，但 Forge 的依赖检查发生在
//       ModDependencyHandler 把内嵌 libs/ 释放到 mods/ 目录【之前】，因此首次启动
//       （mods/ 中还没有 kotlinforforge）必然失败，ModDependencyHandler 根本来不及运行。
// 解决：把 KotlinForForge 的运行时子模块通过 Forge 官方 JarInJar 机制内嵌到
//       META-INF/jarjar/。Forge 的 JarInJarDependencyLocator 在 mod 扫描 / 依赖检查
//       阶段（早于任何 mod 代码）就会自动加载它们：
//         - kfflang (LANGPROVIDER) : kotlinforforge 语言加载器
//         - kfflib  (GAMELIBRARY)  : Kotlin 运行时库（kotlin-stdlib 等）
//         - kffmod  (MOD)          : mod 本体，modId = "kotlinforforge" -> 满足依赖检查
//       若 mods/ 中已有外部 kotlinforforge（或 kotlinforforge-all），JarSelector 会
//       优先使用已加载版本并跳过内嵌副本（自动去重，无重复模块冲突）。
// 来源：src/main/libs/kotlinforforge-<v>-all.jar 内 META-INF/jarjar/*.jar
val kotlinForForgeVersion = "4.12.0"
val kotlinForForgeBundleJar = "kotlinforforge-$kotlinForForgeVersion-all.jar"
val kotlinForForgeJarJarModules = listOf(
    "kfflang-$kotlinForForgeVersion.jar",
    "kfflib-$kotlinForForgeVersion.jar",
    "kffmod-$kotlinForForgeVersion.jar",
)
val jarJarStagingDir = layout.buildDirectory.dir("generated/jarjar")

// 从 kotlinforforge-all 提取运行时子模块到 build/generated/jarjar/
val extractKotlinForForge by tasks.registering {
    description = "从 kotlinforforge-all 提取 JarInJar 运行时子模块（kfflang/kfflib/kffmod）"
    val bundleFile = file("src/main/libs/$kotlinForForgeBundleJar")
    inputs.file(bundleFile)
    outputs.dir(jarJarStagingDir)
    doLast {
        val outDir = jarJarStagingDir.get().asFile
        outDir.mkdirs()
        ZipFile(bundleFile).use { zip ->
            for (module in kotlinForForgeJarJarModules) {
                val entry = zip.getEntry("META-INF/jarjar/$module")
                    ?: throw GradleException("$kotlinForForgeBundleJar 中缺少 META-INF/jarjar/$module")
                val out = File(outDir, module)
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        logger.lifecycle("[build.gradle.kts] 已提取 ${kotlinForForgeJarJarModules.size} 个 KotlinForForge JarInJar 子模块 -> $outDir")
    }
}

// 生成 META-INF/jarjar/metadata.json（Forge JarInJar 依赖声明，格式与 jarjar 库一致）
val generateKotlinForForgeMetadata by tasks.registering {
    description = "生成 META-INF/jarjar/metadata.json（KotlinForForge 运行时依赖声明）"
    inputs.property("kffVersion", kotlinForForgeVersion)
    inputs.property("modules", kotlinForForgeJarJarModules)
    val metadataFile = jarJarStagingDir.map { it.file("metadata.json") }
    outputs.file(metadataFile)
    doLast {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"jars\": [")
        for ((idx, module) in kotlinForForgeJarJarModules.withIndex()) {
            val artifact = module.substringBefore("-") // kfflang / kfflib / kffmod
            val comma = if (idx < kotlinForForgeJarJarModules.size - 1) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"identifier\": {")
            sb.appendLine("        \"group\": \"thedarkcolour\",")
            sb.appendLine("        \"artifact\": \"$artifact\"")
            sb.appendLine("      },")
            sb.appendLine("      \"version\": {")
            sb.appendLine("        \"range\": \"[$kotlinForForgeVersion,)\",")
            sb.appendLine("        \"artifactVersion\": \"$kotlinForForgeVersion\"")
            sb.appendLine("      },")
            sb.appendLine("      \"path\": \"META-INF/jarjar/$module\",")
            sb.appendLine("      \"isObfuscated\": false")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        metadataFile.get().asFile.writeText(sb.toString(), Charsets.UTF_8)
        logger.lifecycle("[build.gradle.kts] 已生成 META-INF/jarjar/metadata.json（${kotlinForForgeJarJarModules.size} 个模块）")
    }
}

// MixinGradle 负责：注入 Mixin 注解处理器 + 生成/重混淆 refmap
// （mixin json 中已手动声明 "refmap": "qlmzombie-thirst.refmap.json" / "qlmzombie.refmap.json"）
configure<org.spongepowered.asm.gradle.plugins.MixinExtension> {
    add(sourceSets.main.get(), "qlmzombie-thirst.refmap.json")
    add(sourceSets.main.get(), "qlmzombie.refmap.json")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjvm-default=all", "-Xskip-metadata-version-check")
    }
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filteringCharset = "UTF-8"
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "forge_version" to forge_version,
        "forge_version_range" to forge_version_range,
        "loader_version_range" to loader_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description,
    )
    inputs.properties(replaceProperties)

    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(replaceProperties + mapOf("project" to project))
    }
}

// 生成 libs/manifest.txt —— 列出所有打包进 mod JAR 的依赖文件名，
// 作为运行时 ModDependencyHandler 的权威白名单源。
// 任何在 manifest 中的 JAR 都会被释放到 mods/ 且永不被本 mod 误删/误禁用。
val libsManifestDir = layout.buildDirectory.dir("generated/libs-manifest")
val generateLibsManifest by tasks.registering {
    description = "生成 libs/manifest.txt 白名单清单（运行时依赖释放依据）"
    outputs.dir(libsManifestDir)
    val libsDir = file("src/main/libs")
    val manifestFile = libsManifestDir.map { it.file("manifest.txt") }
    // CI 环境可能没有 src/main/libs（.gitignore 排除），用可选方式声明输入
    if (libsDir.exists()) {
        inputs.dir(libsDir)
    }
    doLast {
        // 剥离 [中文名] 前缀后检查排除规则，确保带前缀的文件也能被正确排除
        // 例如 "[某名] qlmzombie-xxx.jar" 也会被排除
        fun stripBracketPrefix(name: String): String {
            val lower = name.lowercase()
            if (lower.startsWith("[")) {
                val close = lower.indexOf(']')
                if (close > 0 && close < lower.length - 1) {
                    return lower.substring(close + 1).trim()
                }
            }
            return lower
        }
        val excludes = listOf(
            Regex("(?i)^qlmzombie.*\\.jar$"),
            Regex("(?i)^serveradmin.*\\.jar$"),
            Regex("(?i)^player2-.*\\.jar$"),
            Regex("(?i)^vanilla_server.*\\.jar$"),
            Regex("(?i)jython"),
            Regex("(?i)graal"),
            Regex("(?i)polyglot"),
            Regex("(?i)\\[python\\]")
        )
        val jarNames = libsDir.listFiles { f ->
            f.isFile && f.name.endsWith(".jar") && !f.name.endsWith(".disabled")
        }?.map { it.name }?.filter { name ->
            // 先检查原始名称，再检查剥离前缀后的名称
            val stripped = stripBracketPrefix(name)
            // JarInJar 内嵌的 KotlinForForge 不再通过 libs/ 释放到 mods/，
            // 避免与 META-INF/jarjar/ 中的内嵌副本重复加载（由 JarInJar 机制直接提供）。
            if (stripped.startsWith("kotlinforforge")) return@filter false
            excludes.none { it.containsMatchIn(name) || it.containsMatchIn(stripped) }
        } ?: emptyList()

        val outDir = manifestFile.get().asFile.parentFile
        outDir.mkdirs()
        val sb = StringBuilder()
        sb.appendLine("# QLM Zombie libs manifest - auto-generated by build.gradle.kts")
        sb.appendLine("# Each line is a JAR file name that will be packaged into libs/ and")
        sb.appendLine("# treated as runtime whitelist source by ModDependencyHandler.")
        sb.appendLine("# Do NOT edit manually; regenerate via :generateLibsManifest")
        sb.appendLine("# Total: ${jarNames.size} jars")
        for (name in jarNames.sorted()) {
            sb.appendLine(name)
        }
        manifestFile.get().asFile.writeText(sb.toString(), Charsets.UTF_8)
        logger.lifecycle("[build.gradle.kts] 生成 libs/manifest.txt: ${jarNames.size} 条目 -> ${manifestFile.get().asFile}")
    }
}

tasks.named<Jar>("jar") {
    // 依赖 manifest 生成任务 + JarInJar 提取/元数据生成任务
    dependsOn(generateLibsManifest, extractKotlinForForge, generateKotlinForForgeMetadata)

    manifest {
        attributes(mapOf(
            "Specification-Title" to mod_id,
            "Specification-Vendor" to mod_authors,
            "Specification-Version" to "1",
            "Implementation-Title" to project.name,
            "Implementation-Version" to archiveVersion,
            "Implementation-Vendor" to mod_authors,
            "Implementation-Timestamp" to Instant.now().toString(),
            "FMLAT" to "accesstransformer.cfg",
        ))
    }

    // JarInJar 内嵌 KotlinForForge 运行时（kfflang/kfflib/kffmod + metadata.json）。
    // Forge 的 JarInJarDependencyLocator 会在 mod 扫描 / 依赖检查阶段（早于任何 mod 代码）
    // 加载它们，使 mods.toml 中 mandatory 的 kotlinforforge 依赖在首次启动即可满足，
    // 不再依赖"先释放到 mods/ 再重启"的兜底流程。
    from(jarJarStagingDir) {
        into("META-INF/jarjar")
    }

    // Embed dependency JARs inside our jar. ModDependencyHandler releases them to
    // mods/ at runtime for OPTIONAL deps (kubejs/cloth-config/etc). kotlinforforge
    // is NOT packaged here anymore - it is provided via META-INF/jarjar (above) so
    // it is available before the dependency check on the very first launch.
    // IMPORTANT: We do NOT extract kotlin class files into the jar root, because
    // that would cause a ResolutionException when kotlinforforge is also loaded
    // (both modules would export the same kotlinx.coroutines.* packages).
    from("src/main/libs") {
        into("libs")
        include("*.jar")
        exclude("README.txt")
        // 排除自身编译产物和不需要打包的 JAR
        exclude("qlmzombie*.jar")
        exclude("serveradmin*")
        exclude("player2-*")
        exclude("vanilla_server*")
        exclude("*jython*")
        exclude("*graal*")
        exclude("*polyglot*")
        exclude("*[Python]*")
        // kotlinforforge 由 META-INF/jarjar/ 提供，不再放入 libs/（避免重复释放/加载）
        exclude("kotlinforforge-*.jar")
        // 同样排除带 [中文名] 前缀的变体
        exclude("*] qlmzombie*")
        exclude("*] serveradmin*")
        exclude("*] player2-*")
        exclude("*] vanilla_server*")
    }

    // 把生成的 manifest.txt 打包到 mod JAR 内的 libs/manifest.txt
    from(libsManifestDir) {
        into("libs")
        include("manifest.txt")
    }

    finalizedBy("reobfJar")
}

/**
 * 构建服务端专用发行包（带 `server` 分类器）。
 *
 * <p>产物：`build/libs/qlmzombie-<version>-server.jar`。
 *
 * <p>与默认 jar（客户端双用）的区别：
 * <ol>
 *   <li>MANIFEST.MF 新增属性 {@code QLM-Server-Release: true} + {@code QLM-Server-Disabled-Prefixes: crafting-dead*}</li>
 *   <li>JAR 根目录新增 {@code server.release.txt} 标记文件，标注：服务端模式、禁用模组清单、构建时间、版本号。</li>
 *   <li>内嵌的 libs 依赖清单（manifest.txt）保持不变 —— 真正的禁用行为由运行时
 *       {@code FMLEnvironment.dist == DEDICATED_SERVER} 判断，所以同一个 server JAR
 *       如果被误用在客户端也能 100% 正常运行（所有 crafting-dead 正常加载）。</li>
 * </ol>
 *
 * <p>运行时行为才是权威来源（FMLEnvironment），分类器 JAR 只作为下载/分发时的人工区分。
 */
val buildServerJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds a DEDICATED_SERVER classifier release JAR."

    // 依赖默认 jar（所有 libs/manifest/classes 已准备好）
    dependsOn(tasks.named("jar"))

    archiveClassifier.set("server")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // 以默认 jar 输出为蓝本（直接复用 reobf 后的 classes / libs / manifest）
    val defaultJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    from(zipTree(defaultJar))

    // 新增 MANIFEST.MF 额外属性（服务端标记）
    manifest {
        attributes(
            mapOf(
                "QLM-Server-Release" to "true",
                "QLM-Server-Disabled-Prefixes" to "crafting-dead, crafting_dead, crafting dead, craftingdead",
                "Implementation-Timestamp" to Instant.now().toString()
            )
        )
    }

    // 写入 JAR 根目录的 server.release.txt 标记文件
    val serverMarkerFile = temporaryDir.resolve("server.release.txt")
    doFirst {
        val disabled = buildList {
            add("crafting-dead-core (Crafting Dead Core - 武器/枪械核心)")
            add("crafting-dead-decoration (Crafting Dead Decoration - 装饰方块)")
            add("crafting-dead-survival (Crafting Dead Survival - 生存机制扩展)")
            add("crafting-dead-worldguard (Crafting Dead WorldGuard - 区域保护集成)")
        }
        serverMarkerFile.writeText(buildString {
            appendLine("QLM Zombie Server Release Marker")
            appendLine("================================")
            appendLine("Version: $mod_version")
            appendLine("Build:   ${Instant.now()}")
            appendLine("Mode:    DEDICATED_SERVER_ONLY (recommended for stand-alone server hosts)")
            appendLine()
            appendLine("Server-Only Disabled Mods (these JARs will be renamed to .disabled on release:)")
            for (d in disabled) {
                appendLine("  - $d")
            }
            appendLine()
            appendLine("Runtime behavior (authoritative, not this file:)")
            appendLine("  FMLEnvironment.dist == DEDICATED_SERVER -> disable SERVER_DISABLED_PREFIXES")
            appendLine("  Note: This JAR is identical in content to the main JAR; classifier only distinguishes downloads.")
        }, Charsets.UTF_8)
    }
    from(serverMarkerFile) {
        into("/")
    }

    // server jar 只是基于 reobf 后的主 jar 再加一层 wrapper（marker + manifest 属性），
    // 不再二次 reobf —— server jar 内的 classes 已经是 reobf 后的产物。
}

// 确保主 jar 构建完 + 跑完 reobf 流程后，立刻产出 server classifier 发行包。
// reobfJar 由 ForgeGradle 在 afterEvaluate 内注册，需在 afterEvaluate 里 findByName 查找。
afterEvaluate {
    val reobfTask = tasks.findByName("reobfJar")
    if (reobfTask != null) {
        reobfTask.finalizedBy(buildServerJar)
        buildServerJar.configure { mustRunAfter(reobfTask) }
    } else {
        // 开发环境/无插件场景：只保证 build server jar 在主 jar 之后
        buildServerJar.configure { mustRunAfter(tasks.named("jar")) }
    }
}