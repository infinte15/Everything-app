package de.finn.everythingapp

import io.flutter.embedding.android.FlutterFragmentActivity

/**
 * FlutterFragmentActivity statt FlutterActivity: local_auth zeigt seinen Dialog ueber
 * androidx.biometric.BiometricPrompt, und der setzt eine FragmentActivity voraus. Mit der
 * einfachen FlutterActivity schlaegt authenticate() auf Android zur Laufzeit fehl.
 */
class MainActivity : FlutterFragmentActivity()
