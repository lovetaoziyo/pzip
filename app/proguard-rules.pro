# Add project specific ProGuard rules here.
# Keep OpenVPN 3 JNI classes (reflection-based SWIG bindings).
-keep class net.openvpn.ovpn3.** { *; }
-keep class com.tim.openvpn.** { *; }
-keep class com.tim.basevpn.** { *; }
