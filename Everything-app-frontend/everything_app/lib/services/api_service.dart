import 'package:dio/dio.dart';


class ApiService {
  late final Dio _dio;
  

  static const String baseUrl = 'http://localhost:8080/api';
  
  //Für Android Emulator: http://10.0.2.2:8080/api

  
  ApiService() {
    _dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 5),
        receiveTimeout: const Duration(seconds: 3),
        headers: {
          'Content-Type': 'application/json',
        },
      ),
    );
    
    // Logging (zeigt alle Requests im Debug)
    _dio.interceptors.add(
      LogInterceptor(
        requestBody: true,
        responseBody: true,
      ),
    );
  }
  
  // Test Endpoint
  Future<Map<String, dynamic>> testConnection() async {
    try {
      final response = await _dio.get('/test/hello');
      return response.data;
    } on DioException catch (e) {
      throw Exception('Connection failed: ${e.message}');
    }
  }
  
  // POST Test
  Future<Map<String, dynamic>> echo(String message) async {
    try {
      final response = await _dio.post(
        '/test/echo',
        data: {'message': message},
      );
      return response.data;
    } on DioException catch (e) {
      throw Exception('Echo failed: ${e.message}');
    }
  }
}