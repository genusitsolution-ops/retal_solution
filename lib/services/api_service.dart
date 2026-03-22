// lib/services/api_service.dart
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/constants.dart';

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

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (_token != null) 'Authorization': 'Bearer $_token',
      };

  Future<ApiResponse> post(String url, Map<String, dynamic> body) async {
    try {
      final response = await http
          .post(
            Uri.parse(url),
            headers: _headers,
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 30));

      return _parseResponse(response);
    } catch (e) {
      return ApiResponse(
        success: false,
        message: _friendlyError(e.toString()),
        statusCode: 0,
      );
    }
  }

  Future<ApiResponse> get(String url) async {
    try {
      final response = await http
          .get(Uri.parse(url), headers: _headers)
          .timeout(const Duration(seconds: 30));

      return _parseResponse(response);
    } catch (e) {
      return ApiResponse(
        success: false,
        message: _friendlyError(e.toString()),
        statusCode: 0,
      );
    }
  }

  Future<ApiResponse> put(String url, Map<String, dynamic> body) async {
    try {
      final response = await http
          .put(
            Uri.parse(url),
            headers: _headers,
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 30));

      return _parseResponse(response);
    } catch (e) {
      return ApiResponse(
        success: false,
        message: _friendlyError(e.toString()),
        statusCode: 0,
      );
    }
  }

  Future<ApiResponse> delete(String url) async {
    try {
      final response = await http
          .delete(Uri.parse(url), headers: _headers)
          .timeout(const Duration(seconds: 30));

      return _parseResponse(response);
    } catch (e) {
      return ApiResponse(
        success: false,
        message: _friendlyError(e.toString()),
        statusCode: 0,
      );
    }
  }

  ApiResponse _parseResponse(http.Response response) {
    try {
      final body = jsonDecode(response.body);
      final success = body['status'] == 'success' ||
          (response.statusCode >= 200 && response.statusCode < 300);
      return ApiResponse(
        success: success,
        message: body['message'] ?? '',
        data: body['data'],
        statusCode: response.statusCode,
      );
    } catch (_) {
      return ApiResponse(
        success: false,
        message: 'Invalid server response',
        statusCode: response.statusCode,
      );
    }
  }

  String _friendlyError(String error) {
    if (error.contains('SocketException') || error.contains('network')) {
      return 'No internet connection. Please check your network.';
    }
    if (error.contains('TimeoutException')) {
      return 'Request timed out. Please try again.';
    }
    return 'Connection failed. Please try again.';
  }
}
