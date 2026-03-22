// lib/screens/employee/emp_tenants_screen.dart
import 'package:flutter/material.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class EmpTenantsScreen extends StatefulWidget {
  const EmpTenantsScreen({super.key});

  @override
  State<EmpTenantsScreen> createState() => _EmpTenantsScreenState();
}

class _EmpTenantsScreenState extends State<EmpTenantsScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  List _tenants = [];

  final _dummy = [
    {
      'full_name': 'Ramesh Kumar',
      'phone': '9876543210',
      'property_code': 'Room-101',
      'rent': 5000,
      'pending': 5000,
      'status': 'active'
    },
    {
      'full_name': 'Sunita Devi',
      'phone': '9812345678',
      'property_code': 'Flat-2B',
      'rent': 9500,
      'pending': 0,
      'status': 'active'
    },
    {
      'full_name': 'Ajay Singh',
      'phone': '9701234567',
      'property_code': 'Room-202',
      'rent': 4200,
      'pending': 4200,
      'status': 'active'
    },
    {
      'full_name': 'Pooja Mishra',
      'phone': '9654321098',
      'property_code': 'Room-105',
      'rent': 4800,
      'pending': 4800,
      'status': 'active'
    },
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final res = await _api.get(ApiConfig.empTenants);
    if (!mounted) return;
    setState(() {
      _loading = false;
      _tenants = (res.success && res.data != null)
          ? (res.data is List ? res.data : [res.data])
          : _dummy;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.accentOrange,
        title: const Text('My Tenants',
            style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              colors: [Color(0xFFE65100), Color(0xFFFF6D00)],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
        ),
      ),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(
                  color: AppTheme.accentOrange))
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: _tenants.length,
                itemBuilder: (_, i) {
                  final t = _tenants[i];
                  final pending =
                      double.tryParse(t['pending'].toString()) ?? 0;
                  final initials = (t['full_name'] ?? 'T')
                      .split(' ')
                      .take(2)
                      .map((s) =>
                          s.isNotEmpty ? s[0].toUpperCase() : '')
                      .join();

                  return Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(14),
                      boxShadow: [
                        BoxShadow(
                            color: Colors.black.withOpacity(0.05),
                            blurRadius: 8,
                            offset: const Offset(0, 2))
                      ],
                    ),
                    child: ListTile(
                      contentPadding: const EdgeInsets.all(14),
                      leading: CircleAvatar(
                        backgroundColor:
                            AppTheme.accentOrange.withOpacity(0.15),
                        radius: 22,
                        child: Text(initials,
                            style: const TextStyle(
                                color: AppTheme.accentOrange,
                                fontWeight: FontWeight.bold)),
                      ),
                      title: Text(t['full_name'] ?? '',
                          style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14)),
                      subtitle: Text(
                          '${t['property_code'] ?? ''} • ${t['phone'] ?? ''}',
                          style: const TextStyle(fontSize: 12)),
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            pending > 0
                                ? '₹${pending.toStringAsFixed(0)} due'
                                : '✓ Paid',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                              color: pending > 0
                                  ? AppTheme.statusPending
                                  : AppTheme.statusPaid,
                            ),
                          ),
                          Text('₹${t['rent'] ?? 0}/mo',
                              style: const TextStyle(
                                  fontSize: 11,
                                  color: AppTheme.textGrey)),
                        ],
                      ),
                      onTap: () {},
                    ),
                  );
                },
              ),
            ),
    );
  }
}
