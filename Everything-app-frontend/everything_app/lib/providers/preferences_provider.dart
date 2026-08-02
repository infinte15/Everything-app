import 'package:flutter/material.dart';
import '../models/user_preferences.dart';
import '../services/preferences_service.dart';

class PreferencesProvider with ChangeNotifier {
  final PreferencesService _service;

  UserPreferences? _prefs;
  bool _isLoading = false;
  bool _isSaving = false;
  String? _error;

  // service ist injizierbar, damit Tests eine Fake-Implementierung reichen können.
  PreferencesProvider({PreferencesService? service})
      : _service = service ?? PreferencesService();

  UserPreferences? get preferences => _prefs;
  bool get isLoading => _isLoading;
  bool get isSaving => _isSaving;
  String? get error => _error;

  Future<void> load() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      _prefs = await _service.getPreferences();
    } catch (e) {
      _error = 'Fehler beim Laden der Einstellungen: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Speichert und übernimmt die Serverantwort, damit lokale und serverseitige
  /// Werte nach einem Teil-Update nicht auseinanderlaufen.
  Future<bool> save(UserPreferences prefs) async {
    _isSaving = true;
    _error = null;
    notifyListeners();
    try {
      _prefs = await _service.updatePreferences(prefs);
      return true;
    } catch (e) {
      _error = 'Fehler beim Speichern der Einstellungen: $e';
      return false;
    } finally {
      _isSaving = false;
      notifyListeners();
    }
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}
