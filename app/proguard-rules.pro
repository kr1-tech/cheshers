# OpenRouter / online transport models are constructed reflectively by Gson.
-keepclassmembers,allowobfuscation class app.cheshers.core.ai.remote.** { <fields>; }
-keepclassmembers,allowobfuscation class app.cheshers.feature.online.protocol.** { <fields>; }
-keep class app.cheshers.core.data.local.entity.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,Annotation
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
# Never enable HTTP logging: authorization headers and game content are private.
