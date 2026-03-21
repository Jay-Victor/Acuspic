# ProGuard rules for Acuspic

# Keep Markwon classes
-keep class io.noties.markwon.** { *; }
-keep class io.noties.prism4j.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}
