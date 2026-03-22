// lib/providers/auth_provider.dart
import 'package:flutter/foundation.dart';
import '../services/api_service.dart';
import '../services/storage_service.dart';
import '../config/constants.dart';

class AuthProvider extends ChangeNotifier {
  final ApiService _api = ApiService();
  final StorageService _storage = StorageService();

  bool _isLoading = false;
  String? _error;
  String? _userType;
  Map<String, dynamic>? _userData;
  String? _token;

  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get userType => _userType ?? _storage.getUserType();
  Map<String, dynamic>? get userData => _userData ?? _storage.getUserData();
  bool get isLoggedIn => _storage.isLoggedIn;
  String? get token => _token ?? _storage.getToken();

  Future<bool> login(String username, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    final response = await _api.post(ApiConfig.loginUrl, {
      'username': username,
      'password': password,
    });

    _isLoading = false;

    if (response.success && response.data != null) {
      _token = response.data['token'];
      _userType = response.data['user_type'];
      _userData = response.data['user'];

      await _storage.saveToken(_token!);
      await _storage.saveUserType(_userType!);
      await _storage.saveUserData(_userData!);
      _api.setToken(_token!);

      notifyListeners();
      return true;
    }

    _error = response.message.isNotEmpty
        ? response.message
        : 'Invalid credentials. Please try again.';
    notifyListeners();
    return false;
  }

  Future<void> logout() async {
    if (_token != null) {
      await _api.post(ApiConfig.logoutUrl, {});
    }
    _api.clearToken();
    await _storage.clearAll();
    _token = null;
    _userType = null;
    _userData = null;
    notifyListeners();
  }

  void restoreSession() {
    final savedToken = _storage.getToken();
    if (savedToken != null) {
      _api.setToken(savedToken);
    }
  }
}
