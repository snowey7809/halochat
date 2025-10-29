# Add project specific ProGuard rules here.
-keep class com.rapo.haloai.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep GGUF related classes
-keep class ggml.** { *; }
