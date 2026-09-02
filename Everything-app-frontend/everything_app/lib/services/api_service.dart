import 'dart:convert';
import 'package:flutter/foundation.dart';
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

  /// Helper to build full URI
  Uri _buildUri(String url) {
    if (url.startsWith('http')) {
      return Uri.parse(url);
    }
    
    // Prepend base URL if it's just a path
    // Remove the '/api' from start of path if the base URL already ends with '/api'
    String path = url;
    final base = ApiConfig.baseUrl; // z.B. https://app.deine-domain.de/api
    
    if (path.startsWith('/api')) {
      path = path.replaceFirst('/api', '');
    }
    
    if (!path.startsWith('/')) {
      path = '/$path';
    }
    
    return Uri.parse('$base$path');
  }

  /// Get Headers
  Future<Map<String, String>> getHeaders() async {
    final token = await getToken();
    if (token == null) {
      debugPrint('🔑 [ApiService] WARNING: No token found in storage!');
    } else {
      debugPrint('🔑 [ApiService] Token found (length: ${token.length})');
    }
    
    return {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  /// GET Request
  /// [timeout] überschreibt [ApiConfig.timeout] für diese eine Anfrage.
  ///
  /// Gebraucht für den Long-Poll auf `/calendar/schedule-status/await`, der absichtlich lange
  /// offen bleibt. Benannt und optional, damit keiner der bestehenden Aufrufe angefasst werden
  /// muss — genau deshalb reicht hier ein Parameter, wo ein Streaming-Ansatz (SSE) eine ganze
  /// zweite Netz-Primitive in dieser Klasse gebraucht hätte.
  Future<http.Response> get(String url, {Duration? timeout}) async {
    final uri = _buildUri(url);
    try {
      final headers = await getHeaders();
      final response = await _client
          .get(uri, headers: headers)
          .timeout(timeout ?? ApiConfig.timeout);
      
      _logRequest('GET', uri.toString(), response.statusCode);
      return response;
    } catch (e) {
      _logError('GET', uri.toString(), e);
      rethrow;
    }
  }

  /// POST Request
  Future<http.Response> post(String url, Map<String, dynamic> body) async {
    final uri = _buildUri(url);
    try {
      final headers = await getHeaders();
      final response = await _client
          .post(
            uri,
            headers: headers,
            body: json.encode(body),
          )
          .timeout(ApiConfig.timeout);
      
      _logRequest('POST', uri.toString(), response.statusCode);
      return response;
    } catch (e) {
      _logError('POST', uri.toString(), e);
      rethrow;
    }
  }

  /// PUT Request
  Future<http.Response> put(String url, Map<String, dynamic> body) async {
    final uri = _buildUri(url);
    try {
      final headers = await getHeaders();
      final response = await _client
          .put(
            uri,
            headers: headers,
            body: json.encode(body),
          )
          .timeout(ApiConfig.timeout);
      
      _logRequest('PUT', uri.toString(), response.statusCode);
      return response;
    } catch (e) {
      _logError('PUT', uri.toString(), e);
      rethrow;
    }
  }

  /// PATCH Request
  ///
  /// Für Teiländerungen, bei denen ein PUT das ganze Objekt aus dem Client
  /// übernehmen würde - etwa die Kategorie einer Buchung oder der Sync-Schalter
  /// eines Bankkontos.
  Future<http.Response> patch(String url, Map<String, dynamic> body) async {
    final uri = _buildUri(url);
    try {
      final headers = await getHeaders();
      final response = await _client
          .patch(
            uri,
            headers: headers,
            body: json.encode(body),
          )
          .timeout(ApiConfig.timeout);

      _logRequest('PATCH', uri.toString(), response.statusCode);
      return response;
    } catch (e) {
      _logError('PATCH', uri.toString(), e);
      rethrow;
    }
  }

  /// DELETE Request
  Future<http.Response> delete(String url) async {
    final uri = _buildUri(url);
    try {
      final headers = await getHeaders();
      final response = await _client
          .delete(uri, headers: headers)
          .timeout(ApiConfig.timeout);
      
      _logRequest('DELETE', uri.toString(), response.statusCode);
      return response;
    } catch (e) {
      _logError('DELETE', uri.toString(), e);
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
    debugPrint('[$method] $url - Status: $statusCode');
  }

  void _logError(String method, String url, dynamic error) {
    debugPrint('[$method] $url - Error: $error');
  }

  /// Close Client
  void dispose() {
    _client.close();
  }
}