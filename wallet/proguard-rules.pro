# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep RecurringPaymentsService and related classes
-keep class de.schildbach.wallet.service.RecurringPaymentsService { *; }
-keep class de.schildbach.wallet.data.RecurringPayment { *; }
-keep class de.schildbach.wallet.data.RecurringPaymentDatabase { *; }

# Keep BitcoinJ classes
-keep class org.bitcoinj.** { *; }
-keep class com.google.bitcoin.** { *; }

# Keep wallet classes
-keep class de.schildbach.wallet.** { *; }

# Keep Configuration class (referenced by WalletTransactionsFragment)
-keep class de.schildbach.wallet.Configuration { *; }
-keepclassmembers class de.schildbach.wallet.Configuration {
    *;
}

# Keep fragments
-keep class de.schildbach.wallet.ui.**Fragment { *; }

# Keep serialization classes
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep R class
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# Keep JobService
-keep class * extends android.app.job.JobService { *; }

# Keep BroadcastReceiver
-keep class * extends android.content.BroadcastReceiver { *; }
