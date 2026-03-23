// lib/providers/language_provider.dart
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../config/app_strings.dart';

class LanguageProvider extends ChangeNotifier {
  static const String _key = 'app_language';
  AppLanguage _language = AppLanguage.english;

  AppLanguage get language => _language;

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_key);
    if (saved == 'hindi') {
      _language = AppLanguage.hindi;
    } else if (saved == 'marathi') {
      _language = AppLanguage.marathi;
    } else {
      _language = AppLanguage.english;
    }
    AppStrings.setLanguage(_language);
  }

  Future<void> setLanguage(AppLanguage lang) async {
    _language = lang;
    AppStrings.setLanguage(lang);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, lang.name);
    notifyListeners();
  }

  String get(String key) => AppStrings.get(key);
}
