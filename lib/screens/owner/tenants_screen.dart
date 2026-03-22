// lib/screens/owner/tenants_screen.dart
import 'package:flutter/material.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class TenantsScreen extends StatefulWidget {
  const TenantsScreen({super.key});

  @override
  State<TenantsScreen> createState() => _TenantsScreenState();
}

class _TenantsScreenState extends State<TenantsScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  List _tenants = [];
  String _search = '';

  final _dummy = [
    {
      'id': 1,
      'full_name': 'Ramesh Kumar Sharma',
      'phone': '9876543210',
      'email': 'ramesh@email.com',
      'property_code': 'Room-101',
      'city': 'Indore',
      'rent': 5000,
      'status': 'active'
    },
    {
      'id': 2,
      'full_name': 'Sunita Devi Patel',
      'phone': '9812345678',
      'email': 'sunita@email.com',
      'property_code': 'Flat-2B',
      'city': 'Indore',
      'rent': 9500,
      'status': 'active'
    },
    {
      'id': 3,
      'full_name': 'Ajay Singh Yadav',
      'phone': '9701234567',
      'email': 'ajay@email.com',
      'property_code': 'Room-202',
      'city': 'Bhopal',
      'rent': 4200,
      'status': 'active'
    },
    {
      'id': 4,
      'full_name': 'Meena Gupta',
      'phone': '9654321098',
      'email': 'meena@email.com',
      'property_code': 'Shop-03',
      'city': 'Raipur',
      'rent': 14000,
      'status': 'inactive'
    },
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final res = await _api.get(ApiConfig.ownerTenants);
    if (!mounted) return;
    setState(() {
      _loading = false;
      _tenants = (res.success && res.data != null)
          ? (res.data is List ? res.data : [res.data])
          : _dummy;
    });
  }

  List get _filtered {
    if (_search.isEmpty) return _tenants;
    final q = _search.toLowerCase();
    return _tenants.where((t) {
      return (t['full_name'] ?? '').toLowerCase().contains(q) ||
          (t['phone'] ?? '').contains(q) ||
          (t['property_code'] ?? '').toLowerCase().contains(q);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        title: const Text('Tenants',
            style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
            child: TextField(
              onChanged: (v) => setState(() => _search = v),
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'Search tenants...',
                hintStyle: const TextStyle(color: Colors.white54),
                prefixIcon:
                    const Icon(Icons.search, color: Colors.white54),
                filled: true,
                fillColor: Colors.white.withOpacity(0.15),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(vertical: 10),
              ),
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {},
        backgroundColor: AppTheme.primary,
        icon: const Icon(Icons.person_add, color: Colors.white),
        label: const Text('Add Tenant',
            style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.primary))
          : _filtered.isEmpty
              ? const EmptyState(
                  icon: Icons.people_outline,
                  title: 'No Tenants Found',
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: _filtered.length,
                    itemBuilder: (_, i) =>
                        _TenantCard(tenant: _filtered[i]),
                  ),
                ),
    );
  }
}

class _TenantCard extends StatelessWidget {
  final Map tenant;

  const _TenantCard({required this.tenant});

  @override
  Widget build(BuildContext context) {
    final initials = (tenant['full_name'] ?? 'T')
        .split(' ')
        .take(2)
        .map((s) => s.isNotEmpty ? s[0].toUpperCase() : '')
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
          backgroundColor: AppTheme.primary.withOpacity(0.15),
          radius: 24,
          child: Text(initials,
              style: const TextStyle(
                  color: AppTheme.primary,
                  fontWeight: FontWeight.bold,
                  fontSize: 15)),
        ),
        title: Text(
          tenant['full_name'] ?? 'Unknown',
          style: const TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 14,
              color: AppTheme.textDark),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 3),
            Row(
              children: [
                const Icon(Icons.apartment_outlined,
                    size: 12, color: AppTheme.textGrey),
                const SizedBox(width: 3),
                Text(tenant['property_code'] ?? '',
                    style: const TextStyle(fontSize: 12)),
                const SizedBox(width: 8),
                const Icon(Icons.phone_outlined,
                    size: 12, color: AppTheme.textGrey),
                const SizedBox(width: 3),
                Text(tenant['phone'] ?? '',
                    style: const TextStyle(fontSize: 12)),
              ],
            ),
          ],
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            StatusBadge(status: tenant['status'] ?? 'active'),
            const SizedBox(height: 4),
            Text(
              '₹${tenant['rent'] ?? 0}/mo',
              style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.primary),
            ),
          ],
        ),
        onTap: () {},
      ),
    );
  }
}
