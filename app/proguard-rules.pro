# Keep model classes that are serialized/deserialized via Gson reflection.
-keep class com.example.leanangletracker.RideSession { *; }
-keep class com.example.leanangletracker.TrackPoint { *; }

# Keep generic type information used by Gson TypeToken.
-keepattributes Signature
