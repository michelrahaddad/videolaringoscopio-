# Regras do ProGuard para o projeto BSafe VideoLaryngoscope

# Preservar informações de debug
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# CRÍTICO: Manter métodos nativos JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# CRÍTICO: Manter as classes dos wrappers com seus nomes de pacotes exatos
# Observação: ffmpegLib usa "com.generalplus" enquanto GPCamLib usa "generalplus.com"
-keep class com.generalplus.ffmpegLib.** { *; }
-keep class generalplus.com.GPCamLib.** { *; }

# Manter enums específicos dos wrappers
-keepclassmembers enum com.generalplus.ffmpegLib.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers enum generalplus.com.GPCamLib.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Manter callbacks e handlers
-keep class * extends android.os.Handler
-keep class * implements android.os.Handler$Callback

# Manter classes principais do app
-keep class com.bsafe.videolaryngoscope.** { *; }

# Manter componentes Android
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Manter GLSurfaceView.Renderer
-keep class * implements android.opengl.GLSurfaceView$Renderer {
    public *;
}

# Manter construtores de View para XML inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Serialização
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Remover logs em release (opcional - descomente se desejar)
#-assumenosideeffects class android.util.Log {
#    public static *** d(...);
#    public static *** v(...);
#}