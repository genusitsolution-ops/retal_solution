// lib/services/storage_service.dart
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../config/constants.dart';

class StorageService {
  static final StorageService _instance = StorageService._internal();
  factory StorageService() => _instance;
  StorageService._internal();

  late SharedPreferences _prefs;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  Future<void> saveToken(String token) async =>
      await _prefs.setString(AppStrings.tokenKey, token);

  String? getToken() => _prefs.getString(AppStrings.tokenKey);

  Future<void> saveUserType(String type) async =>
      await _prefs.setString(AppStrings.userTypeKey, type);

  String? getUserType() => _prefs.getString(AppStrings.userTypeKey);

  Future<void> saveUserData(Map<String, dynamic> data) async =>
      await _prefs.setString(AppStrings.userDataKey, jsonEncode(data));

  Map<String, dynamic>? getUserData() {
    final str = _prefs.getString(AppStrings.userDataKey);
    if (str == null) return null;
    return jsonDecode(str) as Map<String, dynamic>;
  }

  Future<void> clearAll() async => await _prefs.clear();

  bool get isLoggedIn => getToken() != null;
}
