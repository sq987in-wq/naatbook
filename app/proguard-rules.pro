# P4f release shrinking policy
#
# Android Gradle Plugin retains manifest-declared components. Hilt, Room, and
# Media3 publish their own consumer rules, and this app deliberately has no
# reflection-driven model serialization or Class.forName entry points. Keep the
# rule set narrow: broad -keep class ** or -dontwarn rules would hide genuine
# shrinker regressions and materially reduce the benefit of R8.

# Preserve useful line information for a privately retained R8 mapping file so
# production crash traces can be retraced without keeping original source names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
