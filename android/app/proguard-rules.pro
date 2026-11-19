# kotlinx.serialization: @Serializable 클래스의 직렬화기 유지
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit 인터페이스와 제네릭 정보
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface com.emotionguard.core.network.ProxyApi
