// lib/config/constants.dart

class ApiConfig {
  static const String baseUrl =
      'https://www.genusitsolution.com/rentalsolutionfinal/api/v1';
  static const String loginUrl = '$baseUrl/auth/login.php';
  static const String logoutUrl = '$baseUrl/auth/logout.php';

  // Owner
  static const String ownerDashboard = '$baseUrl/owner/dashboard.php';
  static const String ownerProperties = '$baseUrl/owner/properties.php';
  static const String ownerTenants = '$baseUrl/owner/tenants.php';
  static const String ownerEmployees = '$baseUrl/owner/employees.php';
  static const String ownerInvoices = '$baseUrl/owner/invoices.php';
  static const String ownerAllocations = '$baseUrl/owner/allocations.php';
  static const String ownerReports = '$baseUrl/owner/reports.php';
  static const String ownerAadhaar = '$baseUrl/owner/aadhaar.php';

  // Employee
  static const String empDashboard = '$baseUrl/employee/dashboard.php';
  static const String empTenants = '$baseUrl/employee/tenants.php';
  static const String empCollections = '$baseUrl/employee/collections.php';

  // Tenant
  static const String tenantDashboard = '$baseUrl/tenant/dashboard.php';
  static const String tenantInvoices = '$baseUrl/tenant/invoices.php';
  static const String tenantProfile = '$baseUrl/tenant/profile.php';
}

class AppStrings {
  static const String appName = 'PRMS';
  static const String appFullName = 'Property Rental Management System';
  static const String tokenKey = 'auth_token';
  static const String userTypeKey = 'user_type';
  static const String userDataKey = 'user_data';
}
