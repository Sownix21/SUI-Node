# Optional compile-time annotations and TLS providers referenced defensively by
# Tink/OkHttp. Android uses its platform TLS provider unless one is installed.
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
