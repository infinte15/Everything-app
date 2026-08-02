import 'package:everything_app/models/user_preferences.dart';
import 'package:everything_app/services/preferences_service.dart';

/// In-memory-Ersatz für [PreferencesService]. Erweitert den echten Service und überschreibt
/// alle Methoden; keine ruft super auf, [ApiService] wird also nie angefasst.
class FakePreferencesService extends PreferencesService {
  FakePreferencesService([UserPreferences? initial])
      : stored = initial ?? const UserPreferences();

  UserPreferences stored;
  UserPreferences? lastSaved;
  int saveCallCount = 0;
  bool failSaves = false;

  @override
  Future<UserPreferences> getPreferences() async => stored;

  @override
  Future<UserPreferences> updatePreferences(UserPreferences prefs) async {
    saveCallCount++;
    if (failSaves) throw Exception('boom');
    lastSaved = prefs;
    stored = prefs;
    return prefs;
  }
}
