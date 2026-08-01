# Keep model classes that are serialized/deserialized via Gson reflection.
-keep class de.hasselmeyer.leanangle.RideSession { *; }
-keep class de.hasselmeyer.leanangle.TrackPoint { *; }

# Keep generic type information used by Gson TypeToken.
-keepattributes Signature
