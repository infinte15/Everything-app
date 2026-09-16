import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, kIsWeb, TargetPlatform;
import 'package:flutter/material.dart';
import 'package:local_auth/local_auth.dart';

/// Legt eine Geraete-Sperre (Fingerabdruck, Gesicht oder Geraete-PIN) vor die App.
///
/// Der Grund ist das Token, nicht das Passwort: nach dem Login liegt ein 30 Tage gueltiges
/// JWT im sicheren Speicher, die App startet also ohne Anmeldung durch. Der Login-Screen
/// schuetzt in diesem Zustand nichts mehr — ein entsperrt liegengelassenes Handy waere ein
/// offener Zugang zu Finanzen, Kalender und Notizen.
///
/// Linux und Web bleiben absichtlich ungesperrt: dort gibt es keine verlaessliche
/// Geraete-Authentifizierung, und ein Gate, das immer durchwinkt, taeuscht Sicherheit nur vor.
class BiometricGate extends StatefulWidget {
  const BiometricGate({super.key, required this.child});

  final Widget child;

  /// Erst nach dieser Zeit im Hintergrund wird wieder gesperrt.
  ///
  /// Bewusst nicht bei jedem Wechsel: der Bank-Login laeuft ueber den externen Browser
  /// (url_launcher) und kehrt per Redirect zurueck. Eine Sperre in genau diesem Moment
  /// wuerde den Rueckweg aus der Bank unterbrechen. Ein kurzer Blick in eine andere App
  /// soll ebenfalls nicht jedes Mal den Fingerabdruck verlangen.
  static const Duration gracePeriod = Duration(seconds: 60);

  @override
  State<BiometricGate> createState() => _BiometricGateState();
}

class _BiometricGateState extends State<BiometricGate> with WidgetsBindingObserver {
  final LocalAuthentication _auth = LocalAuthentication();

  bool _locked = true;
  bool _authenticating = false;
  DateTime? _leftAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Nicht direkt in initState: der erste Frame soll stehen, bevor der Systemdialog kommt.
    WidgetsBinding.instance.addPostFrameCallback((_) => _unlock());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden) {
      _leftAt = DateTime.now();
      return;
    }

    if (state != AppLifecycleState.resumed) return;

    final leftAt = _leftAt;
    _leftAt = null;
    if (leftAt == null) return;
    if (DateTime.now().difference(leftAt) < BiometricGate.gracePeriod) return;

    setState(() => _locked = true);
    _unlock();
  }

  /// Ob auf dieser Plattform ueberhaupt gesperrt wird. `dart:io` ist hier bewusst nicht
  /// importiert — das wuerde den Web-Build brechen.
  bool get _platformIsGated {
    if (kIsWeb) return false;
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
      case TargetPlatform.iOS:
      case TargetPlatform.windows:
      case TargetPlatform.macOS:
        return true;
      default:
        return false;
    }
  }

  Future<void> _unlock() async {
    if (_authenticating) return;
    _authenticating = true;

    try {
      if (!_platformIsGated || !await _auth.isDeviceSupported()) {
        if (mounted) setState(() => _locked = false);
        return;
      }

      // local_auth 3.x nimmt die Optionen einzeln entgegen; das fruehere
      // AuthenticationOptions-Objekt gibt es hier nicht mehr.
      final ok = await _auth.authenticate(
        localizedReason: 'Everything App entsperren',
        // false: die Geraete-PIN ist der Ausweichweg, wenn der Finger nicht erkannt wird.
        biometricOnly: false,
        // Hiess frueher stickyAuth: schickt Android die App waehrend des Dialogs in den
        // Hintergrund, wird die Pruefung beim Zurueckkommen fortgesetzt statt abgebrochen.
        persistAcrossBackgrounding: true,
      );
      if (mounted && ok) setState(() => _locked = false);
    } on LocalAuthException catch (e) {
      // Genau ein Fall rechtfertigt das Durchwinken: das Geraet hat ueberhaupt keine
      // Sperre — weder Biometrie noch PIN. Dann gibt es nichts, wogegen geprueft werden
      // koennte, und dauerhaftes Sperren schloesse den Nutzer aus seinen eigenen Daten aus.
      //
      // Alles andere — Abbruch, Zeitueberschreitung, Sperre nach zu vielen Fehlversuchen —
      // bleibt gesperrt; der Knopf im Sperrbildschirm startet einen neuen Versuch. Bewusst
      // keine erschoepfende Aufzaehlung: das Paket darf laut eigener Zusage jederzeit neue
      // Codes ergaenzen, und alles Unbekannte muss auf der sicheren Seite landen.
      if (mounted && e.code == LocalAuthExceptionCode.noCredentialsSet) {
        setState(() => _locked = false);
      }
    } finally {
      _authenticating = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    // Stack statt Austausch des Kindes: die App behaelt hinter dem Sperrbildschirm ihren
    // Zustand, ein Entsperren fuehrt also zurueck auf denselben Screen.
    return Stack(
      children: [
        widget.child,
        if (_locked) _LockScreen(onUnlock: _unlock),
      ],
    );
  }
}

class _LockScreen extends StatelessWidget {
  const _LockScreen({required this.onUnlock});

  final VoidCallback onUnlock;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Positioned.fill(
      child: Material(
        // Deckend: verdeckt den Inhalt auch in der App-Umschaltansicht des Systems.
        color: theme.colorScheme.surface,
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.lock_outline, size: 48, color: theme.colorScheme.primary),
              const SizedBox(height: 16),
              Text('Everything App ist gesperrt', style: theme.textTheme.titleMedium),
              const SizedBox(height: 24),
              // Sichtbarer Weg zurueck, wenn der Dialog weggewischt wurde.
              FilledButton.icon(
                onPressed: onUnlock,
                icon: const Icon(Icons.fingerprint),
                label: const Text('Entsperren'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
