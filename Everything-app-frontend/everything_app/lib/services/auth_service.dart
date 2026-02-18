
import '../config/api_config.dart';
import 'api_service.dart';


class AuthService {
  final ApiService _apiService = ApiService();
  // Returns: Map mit success, data/error
  Future<Map<String, dynamic>> login(String username, String password) async {
    try {
      final response = await _apiService.post(
        ApiConfig.login,
        {
          'username': username,
          'password': password,
        },
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        
        // Token speichern
        await _apiService.saveToken(data['token']);
        
        return {
          'success': true,
          'data': data,
        };
      } else {
        return {
          'success': false,
          'error': _apiService.getErrorMessage(response),
        };
      }
    } catch (e) {
      return {
        'success': false,
        'error': 'Verbindungsfehler: $e',
      };
    }
  }

  /// Register
  Future<Map<String, dynamic>> register(
    String username,
    String email,
    String password,
  ) async {
    try {
      final response = await _apiService.post(
        ApiConfig.register,
        {
          'username': username,
          'email': email,
          'password': password,
        },
      );

      if (_apiService.isSuccess(response)) {
        final data = _apiService.parseResponse(response);
        
        // Token speichern
        await _apiService.saveToken(data['token']);
        
        return {
          'success': true,
          'data': data,
        };
      } else {
        return {
          'success': false,
          'error': _apiService.getErrorMessage(response),
        };
      }
    } catch (e) {
      return {
        'success': false,
        'error': 'Verbindungsfehler: $e',
      };
    }
  }

  /// Logout
  Future<void> logout() async {
    await _apiService.deleteToken();
  }

  /// Check if logged in
  Future<bool> isLoggedIn() async {
    final token = await _apiService.getToken();
    return token != null;
  }

  /// Get current token
  Future<String?> getToken() async {
    return await _apiService.getToken();
  }
}