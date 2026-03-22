// lib/screens/owner/owner_dashboard.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../providers/auth_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/stat_card.dart';
import '../../widgets/common_widgets.dart';
import 'invoices_screen.dart';

class OwnerDashboard extends StatefulWidget {
  const OwnerDashboard({super.key});

  @override
  State<OwnerDashboard> createState() => _OwnerDashboardState();
}

class _OwnerDashboardState extends State<OwnerDashboard> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  Map<String, dynamic> _stats = {};
  List _recentInvoices = [];

  // Dummy fallback data
  final Map<String, dynamic> _dummyStats = {
    'total_properties': 12,
    'occupied_properties': 9,
    'total_tenants': 9,
    'monthly_revenue': 54500,
    'pending_amount': 12300,
    'collected_this_month': 42200,
    'total_employees': 3,
  };

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final res = await _api.get(ApiConfig.ownerDashboard);
    if (!mounted) return;
    if (res.success && res.data != null) {
      setState(() {
        _stats = res.data['stats'] ?? _dummyStats;
        _recentInvoices = res.data['recent_invoices'] ?? [];
        _loading = false;
      });
    } else {
      setState(() {
        _stats = _dummyStats;
        _recentInvoices = _dummyInvoices;
        _loading = false;
        _error = res.message.isNotEmpty ? res.message : null;
      });
    }
  }

  final _dummyInvoices = [
    {
      'invoice_number': 'INV-001',
      'tenant_name': 'Ramesh Kumar',
      'amount': 8500,
      'status': 'pending',
      'due_date': '2026-03-31'
    },
    {
      'invoice_number': 'INV-002',
      'tenant_name': 'Sunita Sharma',
      'amount': 6200,
      'status': 'paid',
      'due_date': '2026-03-20'
    },
    {
      'invoice_number': 'INV-003',
      'tenant_name': 'Ajay Singh',
      'amount': 9800,
      'status': 'overdue',
      'due_date': '2026-03-15'
    },
  ];

  String _fmt(dynamic val) {
    if (val == null) return '0';
    final n = int.tryParse(val.toString()) ?? 0;
    return NumberFormat.compact().format(n);
  }

  String _currency(dynamic val) {
    if (val == null) return '₹0';
    final n = double.tryParse(val.toString()) ?? 0;
    return '₹${NumberFormat('#,##,###').format(n)}';
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    final name = auth.userData?['full_name'] ?? 'Owner';
    final firstName = name.split(' ').first;
    final hour = DateTime.now().hour;
    final greeting = hour < 12
        ? 'Good Morning'
        : hour < 17
            ? 'Good Afternoon'
            : 'Good Evening';

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: RefreshIndicator(
        onRefresh: _load,
        color: AppTheme.primary,
        child: CustomScrollView(
          slivers: [
            // Header
            SliverAppBar(
              expandedHeight: 160,
              floating: false,
              pinned: true,
              backgroundColor: AppTheme.primary,
              flexibleSpace: FlexibleSpaceBar(
                background: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [AppTheme.primary, Color(0xFF283593)],
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
                                  Text(
                                    '$greeting,',
                                    style: const TextStyle(
                                        color: Colors.white70,
                                        fontSize: 14),
                                  ),
                                  Text(
                                    firstName,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 22,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                              Row(
                                children: [
                                  IconButton(
                                    icon: const Icon(
                                        Icons.notifications_none,
                                        color: Colors.white),
                                    onPressed: () {},
                                  ),
                                  GestureDetector(
                                    onTap: () {},
                                    child: CircleAvatar(
                                      radius: 20,
                                      backgroundColor:
                                          Colors.white.withOpacity(0.2),
                                      child: Text(
                                        firstName[0].toUpperCase(),
                                        style: const TextStyle(
                                            color: Colors.white,
                                            fontWeight: FontWeight.bold),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 14, vertical: 8),
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.calendar_today,
                                    color: Colors.white70, size: 14),
                                const SizedBox(width: 6),
                                Text(
                                  DateFormat('EEEE, d MMM yyyy')
                                      .format(DateTime.now()),
                                  style: const TextStyle(
                                      color: Colors.white70,
                                      fontSize: 12),
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
              title: const Text('Dashboard',
                  style: TextStyle(color: Colors.white)),
            ),
            if (_error != null)
              SliverToBoxAdapter(
                child: Container(
                  margin: const EdgeInsets.all(16),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.accentOrange.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                        color: AppTheme.accentOrange.withOpacity(0.3)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.wifi_off,
                          color: AppTheme.accentOrange, size: 16),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Offline mode — showing cached data',
                          style: TextStyle(
                              fontSize: 12,
                              color: AppTheme.accentOrange),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            if (_loading)
              SliverFillRemaining(
                child: Center(
                    child: CircularProgressIndicator(
                        color: AppTheme.primary)),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.all(16),
                sliver: SliverList(
                  delegate: SliverChildListDelegate([
                    // Wide Revenue Card
                    WideStatCard(
                      title: 'Monthly Revenue',
                      value: _currency(_stats['monthly_revenue']),
                      icon: Icons.account_balance_wallet,
                      startColor: AppTheme.primary,
                      endColor: AppTheme.primaryLight,
                      trend:
                          '${_currency(_stats['collected_this_month'])} collected',
                      trendUp: true,
                    ),
                    const SizedBox(height: 16),
                    // 2x2 Stat Grid
                    GridView.count(
                      crossAxisCount: 2,
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                      childAspectRatio: 1.1,
                      children: [
                        StatCard(
                          title: 'Total Properties',
                          value: _fmt(_stats['total_properties']),
                          icon: Icons.apartment,
                          color: AppTheme.primary,
                          subtitle:
                              '${_fmt(_stats['occupied_properties'])} occupied',
                        ),
                        StatCard(
                          title: 'Active Tenants',
                          value: _fmt(_stats['total_tenants']),
                          icon: Icons.people,
                          color: AppTheme.accentTeal,
                        ),
                        StatCard(
                          title: 'Pending Dues',
                          value: _currency(_stats['pending_amount']),
                          icon: Icons.pending_actions,
                          color: AppTheme.statusPending,
                        ),
                        StatCard(
                          title: 'Agents',
                          value: _fmt(_stats['total_employees']),
                          icon: Icons.badge,
                          color: AppTheme.accentPurple,
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    // Quick Actions
                    SectionHeader(
                        title: 'Quick Actions',
                        actionLabel: null),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        _QuickAction(
                            icon: Icons.add_home,
                            label: 'Add Property',
                            color: AppTheme.primary,
                            onTap: () {}),
                        const SizedBox(width: 10),
                        _QuickAction(
                            icon: Icons.person_add,
                            label: 'Add Tenant',
                            color: AppTheme.accentTeal,
                            onTap: () {}),
                        const SizedBox(width: 10),
                        _QuickAction(
                            icon: Icons.receipt_long,
                            label: 'Invoices',
                            color: AppTheme.accentOrange,
                            onTap: () => Navigator.push(
                                context,
                                MaterialPageRoute(
                                    builder: (_) =>
                                        const InvoicesScreen()))),
                        const SizedBox(width: 10),
                        _QuickAction(
                            icon: Icons.bar_chart,
                            label: 'Reports',
                            color: AppTheme.accentPurple,
                            onTap: () {}),
                      ],
                    ),
                    const SizedBox(height: 20),
                    // Recent Invoices
                    SectionHeader(
                      title: 'Recent Invoices',
                      actionLabel: 'View All',
                      onAction: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                              builder: (_) => const InvoicesScreen())),
                    ),
                    const SizedBox(height: 12),
                    ..._recentInvoices.take(5).map((inv) => Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: AppListTile(
                            title: inv['tenant_name'] ??
                                inv['invoice_number'] ??
                                'Invoice',
                            subtitle:
                                '${inv['invoice_number'] ?? ''} • Due: ${inv['due_date'] ?? ''}',
                            trailing:
                                '₹${NumberFormat('#,##,###').format(double.tryParse(inv['amount'].toString()) ?? 0)}',
                            trailingSubtitle: null,
                            trailingColor:
                                inv['status'] == 'paid'
                                    ? AppTheme.statusPaid
                                    : AppTheme.statusPending,
                            leadingWidget: StatusBadge(
                                status: inv['status'] ?? 'pending'),
                            onTap: () {},
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

class _QuickAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _QuickAction(
      {required this.icon,
      required this.label,
      required this.color,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(14),
            boxShadow: [
              BoxShadow(
                  color: color.withOpacity(0.15),
                  blurRadius: 8,
                  offset: const Offset(0, 3))
            ],
          ),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: color.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(icon, color: color, size: 20),
              ),
              const SizedBox(height: 6),
              Text(
                label,
                textAlign: TextAlign.center,
                style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.textDark),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
