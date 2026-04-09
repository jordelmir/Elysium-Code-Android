# Elysium Code ProGuard Rules

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep inference callback
-keep class com.elysium.code.ai.InferenceCallback { *; }
-keep class com.elysium.code.ai.LlamaEngine { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.elysium.code.**$$serializer { *; }
-keepclassmembers class com.elysium.code.** { *** Companion; }
-keepclasseswithmembers class com.elysium.code.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep data classes used with serialization
-keep class com.elysium.code.memory.** { *; }
-keep class com.elysium.code.agent.ChatMessage { *; }
-keep class com.elysium.code.agent.SavedConversation { *; }
-keep class com.elysium.code.agent.ConversationSummary { *; }
-keep class com.elysium.code.plugins.Personality { *; }
-keep class com.elysium.code.plugins.PluginManifest { *; }
-keep class com.elysium.code.mcp.McpServerConfig { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
