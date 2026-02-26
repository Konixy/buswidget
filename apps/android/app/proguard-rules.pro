# Moshi
-keep class com.buswidget.widget.** { *; }
-keep class com.buswidget.data.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
