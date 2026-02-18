import 'package:flutter/material.dart';
import '../services/auth_service.dart';


class AuthProvider with ChangeNotifier {
  final AuthService _authService = AuthService();
  
  bool _isLoggedIn = false;
  bool _isLoading = false;
  String? _username;
  String? _email;
  int? _userId;
  String? _error;

  // Getters
  bool get isLoggedIn => _isLoggedIn;
  bool get isLoading => _isLoading;
  String? get username => _username;
  String? get email => _email;
  int? get userId => _userId;
  String? get error => _error;


  Future<void> checkLoginStatus() async {
    _isLoading = true;
    notifyListeners();

    _isLoggedIn = await _authService.isLoggedIn();
    
    _isLoading = false;
    notifyListeners();
  }


  Future<bool> login(String username, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    final result = await _authService.login(username, password);

    if (result['success']) {
      _isLoggedIn = true;
      _username = result['data']['username'];
      _email = result['data']['email'];
      _userId = result['data']['userId'];
      _error = null;
    } else {
      _error = result['error'];
    }

    _isLoading = false;
    notifyListeners();

    return result['success'];
  }


  Future<bool> register(String username, String email, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    final result = await _authService.register(username, email, password);

    if (result['success']) {
      _isLoggedIn = true;
      _username = result['data']['username'];
      _email = result['data']['email'];
      _userId = result['data']['userId'];
      _error = null;
    } else {
      _error = result['error'];
    }

    _isLoading = false;
    notifyListeners();

    return result['success'];
  }

  Future<void> logout() async {
    await _authService.logout();
    
    _isLoggedIn = false;
    _username = null;
    _email = null;
    _userId = null;
    _error = null;
    
    notifyListeners();
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}