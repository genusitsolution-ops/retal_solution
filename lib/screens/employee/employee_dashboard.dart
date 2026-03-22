// lib/screens/employee/employee_dashboard.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../providers/auth_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/stat_card.dart';
import '../../widgets/common_widgets.dart';

class EmployeeDashboard extends StatefulWidget {
  const EmployeeDashboard({super.key});

  @override
  State<EmployeeDashboard> createState() => _EmployeeDashboardState();
}

class _EmployeeDashboardState extends State<EmployeeDashboard> {
  final ApiService _api = ApiService();
  bool _loading = true;
  Map _stats = {
    'assigned_properties': 4,
    'total_tenants': 4,
    'collections_this_month': 28500,
    'pending_collections': 12000,
    'todays_collections': 5000,
  };
  List _recentColl = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final res = await _api.get(ApiConfig.empDashboard);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _stats = res.data['stats'] ?? _stats;
        _recentColl = res.data['recent_collections'] ?? [];
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    final name = auth.userData?['full_name'] ?? 'Agent';
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: RefreshIndicator(
        onRefresh: _load,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              expandedHeight: 140,
              pinned: true,
              backgroundColor: AppTheme.accentOrange,
              flexibleSpace: FlexibleSpaceBar(
                background: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [Color(0xFFE65100), Color(0xFFFF6D00)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                  ),
                  child: SafeArea(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
                      child: Row(
                        mainAxisAlignment:
                            MainAxisAlignment.spaceBetween,
                        children: [
                          Column(
                            crossAxisAlignment:
                                CrossAxisAlignment.start,
                            children: [
                              const Text('Agent Dashboard',
                                  style: TextStyle(
                                      color: Colors.white70,
                                      fontSize: 13)),
                              Text(
                                name.split(' ').first,
                                style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 22,
                                    fontWeight: FontWeight.bold),
                              ),
                              Text(
                                DateFormat('d MMM yyyy')
                                    .format(DateTime.now()),
                                style: const TextStyle(
                                    color: Colors.white60,
                                    fontSize: 12),
                              ),
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
                    ),
                  ),
                ),
              ),
              title: const Text('Dashboard',
                  style: TextStyle(color: Colors.white)),
            ),
            _loading
                ? const SliverFillRemaining(
                    child: Center(
                        child: CircularProgressIndicator(
                            color: AppTheme.accentOrange)))
                : SliverPadding(
                    padding: const EdgeInsets.all(16),
                    sliver: SliverList(
                      delegate: SliverChildListDelegate([
                        WideStatCard(
                          title: "This Month's Collections",
                          value:
                              '₹${NumberFormat('#,##,###').format(_stats['collections_this_month'] ?? 0)}',
                          icon: Icons.account_balance_wallet,
                          startColor: const Color(0xFFE65100),
                          endColor: const Color(0xFFFF6D00),
                          trend: "Today: ₹${_stats['todays_collections'] ?? 0}",
                        ),
                        const SizedBox(height: 16),
                        GridView.count(
                          crossAxisCount: 2,
                          shrinkWrap: true,
                          physics:
                              const NeverScrollableScrollPhysics(),
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                          childAspectRatio: 1.15,
                          children: [
                            StatCard(
                              title: 'My Properties',
                              value:
                                  '${_stats['assigned_properties'] ?? 0}',
                              icon: Icons.apartment,
                              color: AppTheme.accentOrange,
                            ),
                            StatCard(
                              title: 'My Tenants',
                              value:
                                  '${_stats['total_tenants'] ?? 0}',
                              icon: Icons.people,
                              color: AppTheme.accentTeal,
                            ),
                            StatCard(
                              title: 'Pending',
                              value:
                                  '₹${NumberFormat.compact().format(_stats['pending_collections'] ?? 0)}',
                              icon: Icons.pending,
                              color: AppTheme.accent,
                            ),
                            StatCard(
                              title: "Today's Target",
                              value: '₹5,000',
                              icon: Icons.flag,
                              color: AppTheme.accentPurple,
                            ),
                          ],
                        ),
                        const SizedBox(height: 20),
                        const SectionHeader(title: 'Recent Collections'),
                        const SizedBox(height: 12),
                        if (_recentColl.isEmpty)
                          const Center(
                            child: Padding(
                              padding: EdgeInsets.all(16),
                              child: Text('No collections yet today',
                                  style: TextStyle(
                                      color: AppTheme.textGrey)),
                            ),
                          )
                        else
                          ..._recentColl.take(5).map((c) => Padding(
                                padding:
                                    const EdgeInsets.only(bottom: 8),
                                child: AppListTile(
                                  title: c['tenant_name'] ?? '',
                                  subtitle:
                                      '${c['property_code'] ?? ''} • ${c['payment_mode'] ?? ''}',
                                  trailing:
                                      '₹${c['amount'] ?? 0}',
                                  trailingColor: AppTheme.statusPaid,
                                  leadingIcon: Icons.check_circle,
                                  iconColor: AppTheme.statusPaid,
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
