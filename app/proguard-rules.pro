# C11CarTool ProGuard/R8 rules
# 项目无外部依赖，主要保护反射/序列化用到的类

# Keep VehicleDataModel (volatile fields, JSON serialization)
-keep class com.c11.cartool.vehicle.VehicleDataModel { *; }

# Keep VehicleCommandDefs (CommandDef inner class)
-keep class com.c11.cartool.vehicle.VehicleCommandDefs { *; }
-keep class com.c11.cartool.vehicle.VehicleCommandDefs$CommandDef { *; }

# Keep LogcatVehicleSource (Sig/State/Result inner classes)
-keep class com.c11.cartool.vehicle.LogcatVehicleSource { *; }
-keep class com.c11.cartool.vehicle.LogcatVehicleSource$Sig { *; }
-keep class com.c11.cartool.vehicle.LogcatVehicleSource$State { *; }
-keep class com.c11.cartool.vehicle.LogcatVehicleSource$Result { *; }

# Keep Sh.Result (public fields accessed directly)
-keep class com.c11.cartool.Sh$Result { *; }

# Keep DashboardSnapshot (public volatile fields)
-keep class com.c11.cartool.dashboard.DashboardSnapshot { *; }
-keep class com.c11.cartool.dashboard.SignalRow { *; }

# Keep VehicleParams (static String[][] accessed by index)
-keep class com.c11.cartool.VehicleParams { *; }

# Keep RealVehicleKeys (static String[][])
-keep class com.c11.cartool.vehicle.RealVehicleKeys { *; }

# Keep all enum values (Level, StepResult, ControlMethod)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Remove logging in release (optional, saves ~10KB)
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
# }
