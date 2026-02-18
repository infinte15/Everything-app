import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../config/api_config.dart';


class ApiService {
  // Secure Storage für Token
  final _storage = const FlutterSecureStorage();
  
  // HTTP Client mit Timeout
  final _client = http.Client();

  /// Get Auth Token
  Future<String?> getToken() async {
    return await _storage.read(key: 'auth_token');
  }

  /// Save Auth Token
  Future<void> saveToken(String token) async {
    await _storage.write(key: 'auth_token', value: token);
  }

  /// Delete Auth Token
  Future<void> deleteToken() async {
    await _storage.delete(key: 'auth_token');
  }

  /// Get Headers
  Future<Map<String, String>> getHeaders() async {
    final token = await getToken();
    
    return {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  /// GET Request
  Future<http.Response> get(String url) async {
    try {
      final headers = await getHeaders();
      final response = await _client
          .get(Uri.parse(url), headers: headers)
          .timeout(ApiConfig.timeout);
      
      _logRequest('GET', url, response.statusCode);
      return response;
    } catch (e) {
      _logError('GET', url, e);
      rethrow;
    }
  }

  /// POST Request
  Future<http.Response> post(String url, Map<String, dynamic> body) async {
    try {
      final headers = await getHeaders();
      final response = await _client
          .post(
            Uri.parse(url),
            headers: headers,
            body: json.encode(body),
          )
          .timeout(ApiConfig.timeout);
      
      _logRequest('POST', url, response.statusCode);
      return response;
    } catch (e) {
      _logError('POST', url, e);
      rethrow;
    }
  }

  /// PUT Request
  Future<http.Response> put(String url, Map<String, dynamic> body) async {
    try {
      final headers = await getHeaders();
      final response = await _client
          .put(
            Uri.parse(url),
            headers: headers,
            body: json.encode(body),
          )
          .timeout(ApiConfig.timeout);
      
      _logRequest('PUT', url, response.statusCode);
      return response;
    } catch (e) {
      _logError('PUT', url, e);
      rethrow;
    }
  }

  /// DELETE Request
  Future<http.Response> delete(String url) async {
    try {
      final headers = await getHeaders();
      final response = await _client
          .delete(Uri.parse(url), headers: headers)
          .timeout(ApiConfig.timeout);
      
      _logRequest('DELETE', url, response.statusCode);
      return response;
    } catch (e) {
      _logError('DELETE', url, e);
      rethrow;
    }
  }

  /// Check if Response is Success
  bool isSuccess(http.Response response) {
    return response.statusCode >= 200 && response.statusCode < 300;
  }

  /// Parse JSON Response
  dynamic parseResponse(http.Response response) {
    if (response.body.isEmpty) return null;
    
    try {
      return json.decode(response.body);
    } catch (e) {
      throw Exception('Failed to parse response: $e');
    }
  }

  /// Handle Error Response
  String getErrorMessage(http.Response response) {
    try {
      final data = json.decode(response.body);
      return data['message'] ?? 'Ein Fehler ist aufgetreten';
    } catch (e) {
      return 'Ein Fehler ist aufgetreten (${response.statusCode})';
    }
  }

  /// Logging
  void _logRequest(String method, String url, int statusCode) {
    print('[$method] $url - Status: $statusCode');
  }

  void _logError(String method, String url, dynamic error) {
    print('[$method] $url - Error: $error');
  }

  /// Close Client
  void dispose() {
    _client.close();
  }
}