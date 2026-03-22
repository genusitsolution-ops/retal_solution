// lib/screens/tenant/tenant_dashboard.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../providers/auth_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/stat_card.dart';
import '../../widgets/common_widgets.dart';

class TenantDashboard extends StatefulWidget {
  const TenantDashboard({super.key});

  @override
  State<TenantDashboard> createState() => _TenantDashboardState();
}

class _TenantDashboardState extends State<TenantDashboard> {
  final ApiService _api = ApiService();
  bool _loading = true;
  Map _data = {};

  final _dummyData = {
    'stats': {
      'current_rent': 9500,
      'pending_amount': 9500,
      'total_paid': 57000,
      'total_invoices': 7,
      'pending_invoices': 1,
    },
    'allocation': {
      'property_code': 'Flat-2B',
      'address': 'Vijay Nagar, Indore',
      'start_date': '2025-09-01',
      'property_type': 'Flat',
    },
    'recent_invoices': [
      {
        'invoice_number': 'INV-2026-002',
        'month': 'March 2026',
        'amount': 9500,
        'status': 'pending',
        'due_date': '2026-03-25'
      },
      {
        'invoice_number': 'INV-2026-001',
        'month': 'February 2026',
        'amount': 9500,
        'status': 'paid',
        'due_date': '2026-02-25'
      },
    ],
  };

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final res = await _api.get(ApiConfig.tenantDashboard);
    if (!mounted) return;
    setState(() {
      _loading = false;
      _data = (res.success && res.data != null) ? res.data : _dummyData;
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    final name = auth.userData?['full_name'] ?? 'Tenant';
    final stats = _data['stats'] ?? _dummyData['stats']!;
    final allocation = _data['allocation'] ?? _dummyData['allocation']!;
    final recentInvoices =
        _data['recent_invoices'] ?? _dummyData['recent_invoices']!;

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: RefreshIndicator(
        onRefresh: _load,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              expandedHeight: 170,
              pinned: true,
              backgroundColor: AppTheme.accentTeal,
              flexibleSpace: FlexibleSpaceBar(
                background: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [Color(0xFF00838F), Color(0xFF00BCD4)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                  ),
                  child: SafeArea(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment:
                                MainAxisAlignment.spaceBetween,
                            children: [
                              Column(
                                crossAxisAlignment:
                                    CrossAxisAlignment.start,
                                children: [
                                  const Text('Welcome back,',
                                      style: TextStyle(
                                          color: Colors.white70,
                                          fontSize: 13)),
                                  Text(name.split(' ').first,
                                      style: const TextStyle(
                                          color: Colors.white,
                                          fontSize: 22,
                                          fontWeight: FontWeight.bold)),
                                ],
                              ),
                              CircleAvatar(
                                backgroundColor:
                                    Colors.white.withOpacity(0.2),
                                radius: 22,
                                child: Text(
                                  name[0].toUpperCase(),
                                  style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 18),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 14),
                          // Property info banner
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 14, vertical: 10),
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.apartment,
                                    color: Colors.white70, size: 18),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        allocation['property_code'] ??
                                            'Not Assigned',
                                        style: const TextStyle(
                                            color: Colors.white,
                                            fontWeight:
                                                FontWeight.bold,
                                            fontSize: 14),
                                      ),
                                      Text(
                                        allocation['address'] ?? '',
                                        style: const TextStyle(
                                            color: Colors.white70,
                                            fontSize: 11),
                                      ),
                                    ],
                                  ),
                                ),
                                Text(
                                  '₹${stats['current_rent'] ?? 0}/mo',
                                  style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 15),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              title: const Text('My Home',
                  style: TextStyle(color: Colors.white)),
            ),
            _loading
                ? const SliverFillRemaining(
                    child: Center(
                        child: CircularProgressIndicator(
                            color: AppTheme.accentTeal)))
                : SliverPadding(
                    padding: const EdgeInsets.all(16),
                    sliver: SliverList(
                      delegate: SliverChildListDelegate([
                        // Pending dues alert
                        if ((stats['pending_amount'] ?? 0) > 0) ...[
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              gradient: const LinearGradient(
                                colors: [
                                  Color(0xFFFF6D00),
                                  Color(0xFFFFAB40)
                                ],
                                begin: Alignment.topLeft,
                                end: Alignment.bottomRight,
                              ),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.warning_amber,
                                    color: Colors.white, size: 32),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      const Text('Payment Due',
                                          style: TextStyle(
                                              color: Colors.white70,
                                              fontSize: 12)),
                                      Text(
                                        '₹${NumberFormat('#,##,###').format(stats['pending_amount'] ?? 0)}',
                                        style: const TextStyle(
                                            color: Colors.white,
                                            fontSize: 22,
                                            fontWeight: FontWeight.bold),
                                      ),
                                    ],
                                  ),
                                ),
                                ElevatedButton(
                                  onPressed: () {},
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.white,
                                    foregroundColor:
                                        AppTheme.accentOrange,
                                    minimumSize: const Size(0, 36),
                                    padding: const EdgeInsets.symmetric(
                                        horizontal: 14),
                                    shape: RoundedRectangleBorder(
                                        borderRadius:
                                            BorderRadius.circular(8)),
                                  ),
                                  child: const Text('View',
                                      style: TextStyle(
                                          fontWeight: FontWeight.bold,
                                          fontSize: 13)),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 16),
                        ],
                        GridView.count(
                          crossAxisCount: 2,
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                          childAspectRatio: 1.15,
                          children: [
                            StatCard(
                              title: 'Total Paid',
                              value:
                                  '₹${NumberFormat.compact().format(stats['total_paid'] ?? 0)}',
                              icon: Icons.check_circle,
                              color: AppTheme.statusPaid,
                            ),
                            StatCard(
                              title: 'Total Invoices',
                              value: '${stats['total_invoices'] ?? 0}',
                              icon: Icons.receipt_long,
                              color: AppTheme.accentTeal,
                            ),
                            StatCard(
                              title: 'Pending Invoices',
                              value:
                                  '${stats['pending_invoices'] ?? 0}',
                              icon: Icons.pending_actions,
                              color: AppTheme.statusPending,
                            ),
                            StatCard(
                              title: 'Since',
                              value: allocation['start_date']
                                      ?.substring(0, 7) ??
                                  '-',
                              icon: Icons.calendar_today,
                              color: AppTheme.primary,
                            ),
                          ],
                        ),
                        const SizedBox(height: 20),
                        const SectionHeader(title: 'Recent Invoices'),
                        const SizedBox(height: 12),
                        ...(recentInvoices as List).map((inv) => Padding(
                              padding:
                                  const EdgeInsets.only(bottom: 8),
                              child: Container(
                                padding: const EdgeInsets.all(14),
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius:
                                      BorderRadius.circular(12),
                                  border: Border(
                                    left: BorderSide(
                                      color: inv['status'] == 'paid'
                                          ? AppTheme.statusPaid
                                          : AppTheme.statusPending,
                                      width: 4,
                                    ),
                                  ),
                                  boxShadow: const [
                                    BoxShadow(
                                        color: Colors.black08,
                                        blurRadius: 6,
                                        offset: Offset(0, 2))
                                  ],
                                ),
                                child: Row(
                                  children: [
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                              inv['month'] ??
                                                  inv['invoice_number'] ??
                                                  '',
                                              style: const TextStyle(
                                                  fontSize: 14,
                                                  fontWeight:
                                                      FontWeight.bold)),
                                          Text(
                                              'Due: ${inv['due_date'] ?? ''}',
                                              style: const TextStyle(
                                                  fontSize: 12,
                                                  color:
                                                      AppTheme.textGrey)),
                                        ],
                                      ),
                                    ),
                                    Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.end,
                                      children: [
                                        Text(
                                          '₹${NumberFormat('#,##,###').format(double.tryParse(inv['amount'].toString()) ?? 0.0)}',
                                          style: const TextStyle(
                                              fontSize: 15,
                                              fontWeight: FontWeight.bold,
                                              color: AppTheme.textDark),
                                        ),
                                        StatusBadge(
                                            status: inv['status'] ??
                                                'pending'),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            )),
                        const SizedBox(height: 20),
                      ]),
                    ),
                  ),
          ],
        ),
      ),
    );
  }
}
