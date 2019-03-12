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

-keepattributes InnerClasses,EnclosingMethod,Signature

-keep class java.lang.ClassValue { *; }
-dontwarn java.lang.ClassValue

-keep class javax.servlet.ServletContextListener { *; }
-keep class javax.servlet.ServletContextEvent { *; }
-dontwarn javax.servlet.ServletContext*

-keep class org.apache.http.client.HttpClient { *; }
-keep class org.apache.http.HttpInetConnection { *; }
-keep class org.apache.http.HttpConnection { *; }
-keep class org.apache.http.entity.AbstractHttpEntity { *; }
-dontwarn org.apache.http.**

-keep class org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

-keep class org.apache.avalon.framework.logger.Logger { *; }
-dontwarn org.apache.avalon.framework.logger.Logger

-keep class org.apache.log4j.Logger { *; }
-keep class org.apache.log4j.Level { *; }
-keep class org.apache.log4j.Priority { *; }
-keep class org.apache.log.Hierarchy { *; }
-keep class org.apache.log.Logger { *; }
-dontwarn org.apache.log4j.**
-dontwarn org.apache.log.**

-keep class com.google.errorprone.annotations.CanIgnoreReturnValue { *; }
-keep class com.google.errorprone.annotations.ForOverride { *; }
-keep class com.google.errorprone.annotations.concurrent.LazyInit { *; }
-dontwarn com.google.errorprone.annotations.**

-keep class org.ietf.jgss.GSSCredential { *; }
-keep class org.ietf.jgss.GSSManager
-keep class org.ietf.jgss.GSSName
-keep class org.ietf.jgss.GSSContext
-keep class org.ietf.jgss.GSSException
-keep class org.ietf.jgss.Oid
-dontwarn org.ietf.jgss.**

-keep class javax.naming.ldap.LdapName { *; }
-keep class javax.naming.ldap.Rdn
-keep class javax.naming.directory.Attribut* { *; }
-keep class javax.naming.NamingException { *; }
-keep class javax.naming.InvalidNameException { *; }
-dontwarn javax.naming.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
