# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers so a crash report from a release build is readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson maps JSON keys onto field names by reflection, so the backup model has to
# survive R8 with its field names intact. Without this, R8 renames them to a, b,
# c, and every backup fails to parse, including the seed bundled with the app,
# which crashes the app on first launch.
-keep class com.gerwinkuijntjes.hours.data.BackupPayload { <fields>; }
-keep class com.gerwinkuijntjes.hours.data.BackupClient { <fields>; }
-keep class com.gerwinkuijntjes.hours.data.BackupVisit { <fields>; }

# Generic type information is what lets Gson resolve List<BackupVisit>.
-keepattributes Signature
-keepattributes *Annotation*

# Gson builds instances of classes without a no-arg constructor through Unsafe.
-dontwarn sun.misc.Unsafe
