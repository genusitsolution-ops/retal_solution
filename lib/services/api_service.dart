// lib/services/api_service.dart
import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiResponse {
  final bool success;
  final String message;
  final dynamic data;
  final int statusCode;

  ApiResponse({
    required this.success,
    required this.message,
    this.data,
    required this.statusCode,
  });
}

class ApiService {
  static final ApiService _instance = ApiService._internal();
  factory ApiService() => _instance;
  ApiService._internal();

  String? _token;
  void setToken(String token) => _token = token;
  void clearToken() => _token = null;
  String? get token => _token;

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (_token != null) 'Authorization': 'Bearer $_token',
      };

  Future<ApiResponse> post(String url, Map<String, dynamic> body) async {
    try {
      final response = await http
          .post(Uri.parse(url), headers: _headers, body: jsonEncode(body))
          .timeout(const Duration(seconds: 30));
      return _parse(response);
    } catch (e) {
      return ApiResponse(success: false, message: _err(e), statusCode: 0);
    }
  }

  Future<ApiResponse> get(String url) async {
    try {
      final response = await http
          .get(Uri.parse(url), headers: _headers)
          .timeout(const Duration(seconds: 30));
      return _parse(response);
    } catch (e) {
      return ApiResponse(success: false, message: _err(e), statusCode: 0);
    }
  }

  Future<ApiResponse> put(String url, Map<String, dynamic> body) async {
    try {
      final response = await http
          .put(Uri.parse(url), headers: _headers, body: jsonEncode(body))
          .timeout(const Duration(seconds: 30));
      return _parse(response);
    } catch (e) {
      return ApiResponse(success: false, message: _err(e), statusCode: 0);
    }
  }

  Future<ApiResponse> delete(String url) async {
    try {
      final response = await http
          .delete(Uri.parse(url), headers: _headers)
          .timeout(const Duration(seconds: 30));
      return _parse(response);
    } catch (e) {
      return ApiResponse(success: false, message: _err(e), statusCode: 0);
    }
  }

  ApiResponse _parse(http.Response response) {
    try {
      final body = jsonDecode(response.body);
      final ok = body['status'] == 'success' ||
          (response.statusCode >= 200 && response.statusCode < 300);
      return ApiResponse(
        success: ok,
        message: body['message'] ?? '',
        data: body['data'],
        statusCode: response.statusCode,
      );
    } catch (_) {
      return ApiResponse(
        success: false,
        message: 'Invalid server response (${response.statusCode})',
        statusCode: response.statusCode,
      );
    }
  }

  String _err(dynamic e) {
    final s = e.toString();
    if (s.contains('SocketException') || s.contains('network'))
      return 'No internet connection';
    if (s.contains('TimeoutException')) return 'Request timed out';
    return 'Connection failed. Please try again.';
  }
}
