import '../config/api_config.dart';
import '../models/user_preferences.dart';
import 'api_service.dart';

/// Zugriff auf /api/user/preferences.
///
/// Anders als CalendarService.getEventsInRange werden Fehler hier NICHT verschluckt —
/// bei den Einstellungen muss der Nutzer merken, wenn Laden oder Speichern fehlschlägt,
/// statt stillschweigend Defaults zu sehen.
class PreferencesService {
  final ApiService _apiService = ApiService();

  Future<UserPreferences> getPreferences() async {
    final response = await _apiService.get(ApiConfig.userPreferences);
    if (_apiService.isSuccess(response)) {
      return UserPreferences.fromJson(_apiService.parseResponse(response));
    }
    throw Exception(_apiService.getErrorMessage(response));
  }

  Future<UserPreferences> updatePreferences(UserPreferences prefs) async {
    final response = await _apiService.put(ApiConfig.userPreferences, prefs.toJson());
    if (_apiService.isSuccess(response)) {
      return UserPreferences.fromJson(_apiService.parseResponse(response));
    }
    throw Exception(_apiService.getErrorMessage(response));
  }
}
